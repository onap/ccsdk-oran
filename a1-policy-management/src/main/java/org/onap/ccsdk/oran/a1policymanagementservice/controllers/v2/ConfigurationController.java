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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.util.Optional;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
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
@Tag(name = ConfigurationController.API_NAME)
public class ConfigurationController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);

    public static final String API_NAME = "Management of configuration";
    public static final String API_DESCRIPTION = "";

    @Autowired
    ConfigurationFile configurationFile;

    @Autowired
    RefreshConfigTask refreshConfigTask;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    @PutMapping(path = Consts.V2_API_ROOT + "/configuration", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the current configuration file with the given configuration", //
            description = "Note that the file is ignored if the Consul is used.")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Configuration updated", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "400", //
                    description = "Invalid configuration provided", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))), //
            @ApiResponse(responseCode = "500", //
                    description = "Something went wrong when replacing the configuration. Try again.", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> putConfiguration(@RequestBody Object configuration) {
        try {
            validateConfigFileIsUsed();
            String configAsString = gson.toJson(configuration);
            JsonObject configJson = JsonParser.parseString(configAsString).getAsJsonObject();
            ApplicationConfigParser configParser = new ApplicationConfigParser();
            configParser.parse(configJson);
            configurationFile.writeFile(configJson);
            logger.info("Configuration changed through REST call.");
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException ioe) {
            logger.warn("Configuration file not written, {}.", ioe.getMessage());
            return ErrorResponse.create("Internal error when writing the configuration.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns the contents of the configuration file", //
            description = "Note that the file contents is not relevant if the Consul is used.") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Configuration", //
                    content = @Content(schema = @Schema(implementation = Object.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "File is not found or readable", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class)))

    })
    public ResponseEntity<Object> getConfiguration() {
        try {
            validateConfigFileIsUsed();
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

    private void validateConfigFileIsUsed() throws ServiceException {
        if (this.refreshConfigTask.isConsulUsed()) {
            throw new ServiceException("Config file not used (Consul is used)", HttpStatus.FORBIDDEN);
        }

    }

}
