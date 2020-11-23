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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.io.IOException;
import java.util.Optional;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ErrorResponse.ErrorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("ConfigurationControllerV2")
@Api(tags = {Consts.V2_CONFIG_API_NAME})
public class ConfigurationController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);

    @Autowired
    ConfigurationFile configurationFile;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    @PutMapping(path = Consts.V2_API_ROOT + "/configuration", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Replace the current configuration with the given configuration")
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Configuration updated", response = VoidResponse.class), //
            @ApiResponse(code = 400, message = "Invalid configuration provided", response = ErrorInfo.class), //
            @ApiResponse(code = 500, message = "Something went wrong when replacing the configuration. Try again.",
                    response = ErrorResponse.ErrorInfo.class) //
    })
    public ResponseEntity<Object> putConfiguration(@RequestBody Object configuration) {
        try {
            String configAsString = gson.toJson(configuration);
            JsonObject configJson = JsonParser.parseString(configAsString).getAsJsonObject();
            ApplicationConfigParser configParser = new ApplicationConfigParser();
            configParser.parse(configJson);
            configurationFile.writeFile(configJson);
            logger.info("Configuration changed through REST call.");
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException ioe) {
            logger.warn("Configuration file not written, {}.", ioe.getMessage());
            return ErrorResponse.create("Internal error when writing the configuration. Try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            return ErrorResponse.create(String.format("Faulty configuration. %s", e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Returns the contents of the configuration file") //
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Configuration", response = Object.class), //
            @ApiResponse(code = 404, message = "File is not found or readable",
                    response = ErrorResponse.ErrorInfo.class)} //
    )
    public ResponseEntity<Object> getConfiguration() {
        try {
            Optional<JsonObject> rootObject = configurationFile.readFile();
            if (rootObject.isPresent()) {
                return new ResponseEntity<>(rootObject.get().toString(), HttpStatus.OK);
            } else {
                return ErrorResponse.create("File does not exist", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
