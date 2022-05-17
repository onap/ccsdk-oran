/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
 * ======================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================LICENSE_END===================================
 */

package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import com.google.common.io.CharStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.validation.constraints.NotNull;

import lombok.Builder;
import lombok.Getter;

import org.json.JSONObject;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Parser for the Json representing of the component configuration.
 */
public class ApplicationConfigParser {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfigParser.class);

    private static final String CONFIG = "config";
    private static final String CONTROLLER = "controller";
    private final ApplicationConfig applicationConfig;

    public ApplicationConfigParser(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    @Builder
    @Getter
    public static class ConfigParserResult {
        private List<RicConfig> ricConfigs;

        private Map<String, ControllerConfig> controllerConfigs;

        private String dmaapConsumerTopicUrl;

        private String dmaapProducerTopicUrl;

    }

    public ConfigParserResult parse(JsonObject root) throws ServiceException {

        validateJsonObjectAgainstSchema(root);

        String dmaapProducerTopicUrl = "";
        String dmaapConsumerTopicUrl = "";

        JsonObject pmsConfigJson = root.getAsJsonObject(CONFIG);

        if (pmsConfigJson == null) {
            throw new ServiceException("Missing root configuration \"" + CONFIG + "\" in JSON: " + root);
        }

        JsonObject json = pmsConfigJson.getAsJsonObject("streams_publishes");
        if (json != null) {
            dmaapProducerTopicUrl = parseDmaapConfig(json);
        }

        json = pmsConfigJson.getAsJsonObject("streams_subscribes");
        if (json != null) {
            dmaapConsumerTopicUrl = parseDmaapConfig(json);
        }

        List<RicConfig> ricConfigs = parseRics(pmsConfigJson);
        Map<String, ControllerConfig> controllerConfigs = parseControllerConfigs(pmsConfigJson);
        checkConfigurationConsistency(ricConfigs, controllerConfigs);

        return ConfigParserResult.builder() //
                .dmaapConsumerTopicUrl(dmaapConsumerTopicUrl) //
                .dmaapProducerTopicUrl(dmaapProducerTopicUrl) //
                .ricConfigs(ricConfigs) //
                .controllerConfigs(controllerConfigs) //
                .build();
    }

    private void validateJsonObjectAgainstSchema(Object object) throws ServiceException {
        if (applicationConfig.getConfigurationFileSchemaPath() == null
                || applicationConfig.getConfigurationFileSchemaPath().isEmpty()) {
            return;
        }

        try {
            String schemaAsString = readSchemaFile();

            JSONObject schemaJSON = new JSONObject(schemaAsString);
            var schema = org.everit.json.schema.loader.SchemaLoader.load(schemaJSON);

            String objectAsString = object.toString();
            JSONObject json = new JSONObject(objectAsString);
            schema.validate(json);
        } catch (Exception e) {
            throw new ServiceException("Json schema validation failure: " + e.toString());
        }
    }

    private String readSchemaFile() throws IOException, ServiceException {
        String filePath = applicationConfig.getConfigurationFileSchemaPath();
        InputStream in = getClass().getResourceAsStream(filePath);
        logger.debug("Reading application schema file from: {} with: {}", filePath, in);
        if (in == null) {
            throw new ServiceException("Could not read application configuration schema file: " + filePath,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return CharStreams.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private void checkConfigurationConsistency(List<RicConfig> ricConfigs,
            Map<String, ControllerConfig> controllerConfigs) throws ServiceException {
        Set<String> ricUrls = new HashSet<>();
        Set<String> ricNames = new HashSet<>();
        for (RicConfig ric : ricConfigs) {
            if (!ricUrls.add(ric.getBaseUrl())) {
                throw new ServiceException("Configuration error, more than one RIC URL: " + ric.getBaseUrl());
            }
            if (!ricNames.add(ric.getRicId())) {
                throw new ServiceException("Configuration error, more than one RIC with name: " + ric.getRicId());
            }
            if (!ric.getControllerName().isEmpty() && controllerConfigs.get(ric.getControllerName()) == null) {
                throw new ServiceException(
                        "Configuration error, controller configuration not found: " + ric.getControllerName());
            }
        }
    }

    private List<RicConfig> parseRics(JsonObject config) throws ServiceException {
        List<RicConfig> result = new ArrayList<>();
        for (JsonElement ricElem : getAsJsonArray(config, "ric")) {
            JsonObject ricJsonObj = ricElem.getAsJsonObject();
            RicConfig ricConfig = RicConfig.builder() //
                    .ricId(get(ricJsonObj, "name", "id", "ricId").getAsString()) //
                    .baseUrl(get(ricJsonObj, "baseUrl").getAsString()) //
                    .managedElementIds(parseManagedElementIds(get(ricJsonObj, "managedElementIds").getAsJsonArray())) //
                    .controllerName(getString(ricJsonObj, CONTROLLER, ""))
                    .customAdapterClass(getString(ricJsonObj, "customAdapterClass", "")) //
                    .build();
            if (!ricConfig.getBaseUrl().isEmpty()) {
                result.add(ricConfig);
            } else {
                logger.error("RIC configuration error {}, baseUrl is empty", ricConfig.getRicId());
            }
        }
        return result;
    }

    String getString(JsonObject obj, String name, String defaultValue) {
        JsonElement elem = obj.get(name);
        if (elem != null) {
            return elem.getAsString();
        }
        return defaultValue;
    }

    Map<String, ControllerConfig> parseControllerConfigs(JsonObject config) throws ServiceException {
        if (config.get(CONTROLLER) == null) {
            return new HashMap<>();
        }
        Map<String, ControllerConfig> result = new HashMap<>();
        for (JsonElement element : getAsJsonArray(config, CONTROLLER)) {
            JsonObject controllerAsJson = element.getAsJsonObject();
            ControllerConfig controllerConfig = ControllerConfig.builder() //
                    .name(get(controllerAsJson, "name").getAsString()) //
                    .baseUrl(get(controllerAsJson, "baseUrl").getAsString()) //
                    .password(get(controllerAsJson, "password").getAsString()) //
                    .userName(get(controllerAsJson, "userName").getAsString()) // )
                    .build();

            if (result.put(controllerConfig.getName(), controllerConfig) != null) {
                throw new ServiceException(
                        "Configuration error, more than one controller with name: " + controllerConfig.getName());
            }
        }
        return result;
    }

    private List<String> parseManagedElementIds(JsonArray asJsonObject) {
        Iterator<JsonElement> iterator = asJsonObject.iterator();
        List<String> managedElementIds = new ArrayList<>();
        while (iterator.hasNext()) {
            managedElementIds.add(iterator.next().getAsString());

        }
        return managedElementIds;
    }

    private static JsonElement get(JsonObject obj, String... alternativeMemberNames) throws ServiceException {
        for (String memberName : alternativeMemberNames) {
            JsonElement elem = obj.get(memberName);
            if (elem != null) {
                return elem;
            }
        }
        throw new ServiceException("Could not find member: " + Arrays.toString(alternativeMemberNames) + " in: " + obj);
    }

    private JsonArray getAsJsonArray(JsonObject obj, String memberName) throws ServiceException {
        return get(obj, memberName).getAsJsonArray();
    }

    private String parseDmaapConfig(JsonObject streamCfg) throws ServiceException {
        Set<Entry<String, JsonElement>> streamConfigEntries = streamCfg.entrySet();
        if (streamConfigEntries.size() != 1) {
            throw new ServiceException(
                    "Invalid configuration. Number of streams must be one, config: " + streamConfigEntries);
        }
        JsonObject streamConfigEntry = streamConfigEntries.iterator().next().getValue().getAsJsonObject();
        JsonObject dmaapInfo = get(streamConfigEntry, "dmaap_info").getAsJsonObject();
        return getAsString(dmaapInfo, "topic_url");
    }

    private static @NotNull String getAsString(JsonObject obj, String memberName) throws ServiceException {
        return get(obj, memberName).getAsString();
    }
}
