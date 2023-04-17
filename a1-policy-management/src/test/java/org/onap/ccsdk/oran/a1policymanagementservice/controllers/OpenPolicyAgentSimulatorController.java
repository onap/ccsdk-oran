/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers;

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

import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.AuthorizationConsts;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.AuthorizationResult;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController("OpenPolicyAgentSimulatorController")
@Tag(name = AuthorizationConsts.API_NAME, description = AuthorizationConsts.API_DESCRIPTION)
public class OpenPolicyAgentSimulatorController {
    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String ACCESS_CONTROL_URL = "/example-authz-check";
    public static final String ACCESS_CONTROL_URL_REJECT = "/example-authz-check-reject";

    public static class TestResults {

        public List<PolicyAuthorizationRequest> receivedRequests =
                Collections.synchronizedList(new ArrayList<PolicyAuthorizationRequest>());

        public TestResults() {}

        public void reset() {
            receivedRequests.clear();

        }
    }

    @Getter
    private TestResults testResults = new TestResults();

    @PostMapping(path = ACCESS_CONTROL_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = AuthorizationConsts.GRANT_ACCESS_SUMMARY,
            description = AuthorizationConsts.GRANT_ACCESS_DESCRIPTION)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", description = "OK", //
                    content = @Content(schema = @Schema(implementation = AuthorizationResult.class))) //
    })
    public ResponseEntity<Object> performAccessControl( //
            @RequestHeader Map<String, String> headers, //
            @RequestBody PolicyAuthorizationRequest request) {
        logger.debug("Auth {}", request);
        testResults.receivedRequests.add(request);

        String res = gson.toJson(AuthorizationResult.builder().result(true).build());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @PostMapping(path = ACCESS_CONTROL_URL_REJECT, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rejecting", description = "", hidden = true)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", description = "OK", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))) //
    })
    public ResponseEntity<Object> performAccessControlReject( //
            @RequestHeader Map<String, String> headers, //
            @RequestBody PolicyAuthorizationRequest request) {
        logger.debug("Auth Reject {}", request);
        testResults.receivedRequests.add(request);
        String res = gson.toJson(AuthorizationResult.builder().result(false).build());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

}
