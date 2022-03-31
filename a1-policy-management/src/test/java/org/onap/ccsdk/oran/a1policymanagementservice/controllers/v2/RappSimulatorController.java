/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2020 Nordix Foundation
 * %%
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.controllers.ServiceCallbackInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController("RappCallbacksController")
@Tag( //
        name = Consts.V2_API_SERVICE_CALLBACKS_NAME, //
        description = Consts.V2_API_SERVICE_CALLBACKS_DESCRIPTION //
)
public class RappSimulatorController {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String SERVICE_CALLBACK_URL = "/r-app/near-rt-ric-status";
    private static Gson gson = new GsonBuilder().create();

    public static class TestResults {
        @Getter
        private Vector<ServiceCallbackInfo> receivedInfo = new Vector<>();

        public List<Map<String, String>> receivedHeaders =
                Collections.synchronizedList(new ArrayList<Map<String, String>>());

        public void clear() {
            receivedInfo.clear();
            receivedHeaders.clear();
        }
    }

    @Getter
    private TestResults testResults = new TestResults();

    private static final String CALLBACK_DESCRIPTION = "The URL to this call is registerred at Service registration.";

    @PostMapping(path = SERVICE_CALLBACK_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Callback for Near-RT RIC status", description = CALLBACK_DESCRIPTION)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = VoidResponse.class)))} //
    )

    public ResponseEntity<Object> serviceCallback( //
            @RequestBody ServiceCallbackInfo body, @RequestHeader Map<String, String> headers) {
        logHeaders(headers);
        logger.info("R-App callback body: {}", gson.toJson(body));
        this.testResults.receivedHeaders.add(headers);
        this.testResults.receivedInfo.add(body);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void logHeaders(Map<String, String> headers) {
        logger.debug("Header begin");
        headers.forEach((key, value) -> logger.debug("  key: {}, value: {}", key, value));
        logger.debug("Header end");
    }

}
