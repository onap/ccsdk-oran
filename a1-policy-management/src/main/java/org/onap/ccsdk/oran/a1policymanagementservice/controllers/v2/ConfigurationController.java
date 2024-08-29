/*-
 * ========================LICENSE_START=================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v2.ConfigurationApi;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.util.Optional;

@RestController("configurationControllerV2")
@Tag( //
        name = ConfigurationController.API_NAME, //
        description = ConfigurationController.API_DESCRIPTION //
)
public class ConfigurationController implements ConfigurationApi {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);

    public static final String API_NAME = "Management of configuration";
    public static final String API_DESCRIPTION = "";

    private final ConfigurationFile configurationFile;
    private final ApplicationConfig applicationConfig;

    ConfigurationController(@Autowired ConfigurationFile configurationFile,
            @Autowired ApplicationConfig applicationConfig) {
        this.configurationFile = configurationFile;
        this.applicationConfig = applicationConfig;
    }

    private static Gson gson = new GsonBuilder() //
            .create(); //

    @Override
    public Mono<ResponseEntity<Object>> putConfiguration(final Mono<Object> configuration,
                                                         final ServerWebExchange exchange) {
        return configuration
                .flatMap(configObject -> {
                    try {
                        String configAsString = gson.toJson(configObject);
                        JsonObject configJson = JsonParser.parseString(configAsString).getAsJsonObject();
                        ApplicationConfigParser configParser = new ApplicationConfigParser(applicationConfig);
                        configParser.parse(configJson);
                        configurationFile.writeFile(configJson);
                        logger.info("Configuration changed through REST call.");
                        return Mono.just(new ResponseEntity<>(HttpStatus.OK));
                    } catch (IOException ioe) {
                        logger.warn("Configuration file not written, {}.", ioe.getMessage());
                        return ErrorResponse.createMono("Internal error when writing the configuration.",
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    } catch (ServiceException e) {
                        return ErrorResponse.createMono(e, HttpStatus.BAD_REQUEST);
                    }
                })
                .doOnError(error -> logger.error(error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<String>> getConfiguration(final ServerWebExchange exchange) throws ServiceException {
            Optional<JsonObject> rootObject = configurationFile.readFile();
            if (rootObject.isPresent()) {
                return Mono.just(new ResponseEntity<>(rootObject.get().toString(), HttpStatus.OK));
            } else {
                throw new ServiceException("File does not exist", HttpStatus.NOT_FOUND);
            }
    }
}
