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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ErrorResponse.ErrorInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ConfigurationFileWriteException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController("ConfigurationControllerV2")
@Api(tags = {Consts.V2_API_NAME})
public class ConfigurationController {

    @Autowired
    ConfigurationFile configurationFile;

    @PutMapping(path = Consts.V2_API_ROOT + "/configuration", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Replace the current configuration with the given configuration")
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Configuration updated", response = VoidResponse.class), //
            @ApiResponse(code = 400, message = "Invalid configuration provided", response = ErrorInfo.class), //
            @ApiResponse(code = 500, message = "Something went wrong when replacing the configuration. Try again.",
                    response = ErrorResponse.ErrorInfo.class) //
    })
    public Mono<ResponseEntity<Object>> putConfiguration(@RequestBody String configuration) {
        try {
            JsonObject configJson = JsonParser.parseString(configuration).getAsJsonObject();
            ApplicationConfigParser configParser = new ApplicationConfigParser();
            configParser.parse(configJson);
            configurationFile.writeFile(configJson);
        } catch (ServiceException | JsonSyntaxException e) {
            ErrorInfo errorInfo = new ErrorInfo(String.format("Faulty configuration. %s", e.getMessage()),
                    HttpStatus.BAD_REQUEST.value());
            return Mono.just(new ResponseEntity<>(errorInfo, HttpStatus.BAD_REQUEST));
        } catch (ConfigurationFileWriteException cfwe) {
            ErrorInfo errorInfo = new ErrorInfo("Internal error when writing the configuration. Try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
            return Mono.just(new ResponseEntity<>(errorInfo, HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return Mono.just(new ResponseEntity<>(HttpStatus.OK));
    }
}
