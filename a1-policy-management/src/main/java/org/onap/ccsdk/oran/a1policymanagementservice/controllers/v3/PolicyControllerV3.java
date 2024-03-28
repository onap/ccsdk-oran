/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v3;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v3.A1PolicyManagementApi;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.PolicyController;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyTypeInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.PolicyService;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@RestController("PolicyControllerV3")
@Tag(//
        name = PolicyController.API_NAME, //
        description = PolicyController.API_DESCRIPTION //
)
@RequestMapping("/a1policymanagement/v3")
public class PolicyControllerV3 implements A1PolicyManagementApi {
    public static final String API_NAME = "A1 Policy Management";
    public static final String API_DESCRIPTION = "API to create,update and get policies or policy definitions";
    @Autowired
    private PolicyService policyService;

    @Autowired
    private Helper helper;

    private PolicyType policyType;

    private Ric ric;
    @Override
    public Mono<ResponseEntity<PolicyObjectInformation>> createPolicy(Mono<PolicyObjectInformation> policyObjectInformation, ServerWebExchange exchange) {
        return policyObjectInformation.flatMap(policyObjectInfo -> {
            return policyService.createPolicyService(policyObjectInfo, exchange);
        });
    }

    @Override
    public Mono<ResponseEntity<Void>> deletePolicy(String policyId, String accept, ServerWebExchange exchange) throws Exception {
        return null;
    }

    @Override
    public Mono<ResponseEntity<Object>> getPolicy(String policyId, String accept, ServerWebExchange exchange) throws Exception {
        return null;
    }

    @Override
    public Mono<ResponseEntity<Flux<PolicyInformation>>> getPolicyIds(String policyTypeId, String nearRtRicId, String serviceId, String typeName, String accept, ServerWebExchange exchange) throws Exception {
        return null;
    }

    @Override
    public Mono<ResponseEntity<Object>> getPolicyTypeDefinition(String policyTypeId, String accept, ServerWebExchange exchange) throws Exception {
        return null;
    }

    @Override
    public Mono<ResponseEntity<Flux<PolicyTypeInformation>>> getPolicyTypes(String nearRtRicId, String typeName, String compatibleWithVersion, String accept, ServerWebExchange exchange) throws Exception {
        return null;
    }

    @Override
    public Mono<ResponseEntity<Object>> putPolicy(String policyId, Mono<Object> body, ServerWebExchange exchange) throws Exception {
        return null;
    }
}
