/*-
 * ========================LICENSE_START=================================
 * Copyright (C) 2020 Nordix Foundation. All rights reserved.
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationFile {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationFile.class);

    final ApplicationConfig appConfig;
    final Gson gson = new Gson();

    @Autowired
    public ConfigurationFile(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    public synchronized Optional<JsonObject> readFile() {
        String filepath = appConfig.getLocalConfigurationFilePath();
        if (!fileExists(filepath)) {
            return Optional.empty();
        }

        try (InputStream inputStream = createInputStream(filepath)) {
            JsonObject rootObject = getJsonElement(inputStream).getAsJsonObject();
            logger.debug("Local configuration file read: {}", filepath);
            return Optional.of(rootObject);
        } catch (Exception e) {
            logger.error("Local configuration file not read: {}, {}", filepath, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized void writeFile(JsonObject content) throws IOException {
        String filepath = appConfig.getLocalConfigurationFilePath();
        try (FileWriter fileWriter = getFileWriter(filepath)) {
            gson.toJson(content, fileWriter);
            logger.debug("Local configuration file written: {}", filepath);
        }
    }

    FileWriter getFileWriter(String filepath) throws IOException {
        return new FileWriter(filepath);
    }

    private boolean fileExists(String filepath) {
        return (new File(filepath).exists());
    }

    private JsonElement getJsonElement(InputStream inputStream) {
        return JsonParser.parseReader(new InputStreamReader(inputStream));
    }

    private InputStream createInputStream(@NotNull String filepath) throws IOException {
        return new BufferedInputStream(new FileInputStream(filepath));
    }
}
