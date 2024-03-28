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

package org.onap.ccsdk.oran.a1policymanagementservice.service.v3;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest.Input.AccessType;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.*;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
public class PolicyService {

    @Autowired
    private Helper helper;

    @Autowired
    private Rics rics;

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    private Policies policies;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private A1ClientFactory a1ClientFactory;

    @Autowired
    private ErrorHandlingService errorHandlingService;

    public Mono<ResponseEntity<PolicyObjectInformation>> createPolicyService
            (PolicyObjectInformation policyObjectInfo, ServerWebExchange serverWebExchange) {
        try {
            if (!helper.jsonSchemaValidation(policyObjectInfo.getPolicyObject()))
                return Mono.error(new ServiceException("Schema validation failed", HttpStatus.BAD_REQUEST));
            Ric ric = rics.getRic(policyObjectInfo.getNearRtRicId());
            PolicyType policyType = policyTypes.getType(policyObjectInfo.getPolicyTypeId());
            Policy policy = helper.buildPolicy(policyObjectInfo, policyType, ric, helper.policyIdGeneration());
            return helper.isPolicyAlreadyCreated(policy,policies)
                    .doOnError(error -> errorHandlingService.handleError(error))
                    .flatMap(policyBuilt -> authorizationService.authCheck(serverWebExchange, policy, AccessType.WRITE))
                    .doOnError(error -> errorHandlingService.handleError(error))
                    .flatMap(policyNotUsed -> ric.getLock().lock(Lock.LockType.SHARED, "createPolicy"))
                    .flatMap(grant -> postPolicy(policy, grant))
                    .map(locationHeaderValue ->
                            new ResponseEntity<PolicyObjectInformation>(policyObjectInfo.policyId(policy.getId()),
                                    helper.createHttpHeaders("location",locationHeaderValue), HttpStatus.CREATED))
                    .doOnError(error -> errorHandlingService.handleError(error));
        } catch (Exception ex) {
            return Mono.error(ex);
        }

    }

    private Mono<String> postPolicy(Policy policy, Lock.Grant grant) {
        return  helper.checkRicStateIdle(policy.getRic())
                .doOnError(error -> errorHandlingService.handleError(error))
                .flatMap(ric -> helper.checkSupportedType(ric, policy.getType()))
                .doOnError(error -> errorHandlingService.handleError(error))
                .flatMap(ric -> a1ClientFactory.createA1Client(ric))
                .flatMap(a1Client -> a1Client.putPolicy(policy))
                .doOnError(error -> errorHandlingService.handleError(error))
                .doOnNext(policyString -> policies.put(policy))
                .doFinally(releaseLock -> grant.unlockBlocking())
                .map(locationHeader -> "https://{apiRoot}/a1policymanagement/v1/policies/"+policy.getId())
                .doOnError(error -> errorHandlingService.handleError(error));
    }
}
