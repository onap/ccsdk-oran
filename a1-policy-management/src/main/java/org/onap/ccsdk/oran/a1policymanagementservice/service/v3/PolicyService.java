/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest.Input.AccessType;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyTypeInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyTypeObject;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.*;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Helper helper;
    private final Rics rics;
    private final PolicyTypes policyTypes;
    private final Policies policies;
    private final AuthorizationService authorizationService;
    private final A1ClientFactory a1ClientFactory;
    private final ErrorHandlingService errorHandlingService;
    private final Gson gson;

    public Mono<ResponseEntity<PolicyObjectInformation>> createPolicyService
            (PolicyObjectInformation policyObjectInfo, ServerWebExchange serverWebExchange) {
        try {
            if (Boolean.FALSE.equals(helper.jsonSchemaValidation(gson.toJson(policyObjectInfo.getPolicyObject(), Map.class))))
                return Mono.error(new ServiceException("Schema validation failed", HttpStatus.BAD_REQUEST));
            Ric ric = rics.getRic(policyObjectInfo.getNearRtRicId());
            PolicyType policyType = policyTypes.getType(policyObjectInfo.getPolicyTypeId());
            Policy policy = helper.buildPolicy(policyObjectInfo, policyType, ric, helper.policyIdGeneration(policyObjectInfo));
            return helper.isPolicyAlreadyCreated(policy,policies)
                    .doOnError(errorHandlingService::handleError)
                    .flatMap(policyBuilt -> authorizationService.authCheck(serverWebExchange, policy, AccessType.WRITE)
                    .doOnError(errorHandlingService::handleError)
                    .flatMap(policyNotUsed -> ric.getLock().lock(Lock.LockType.SHARED, "createPolicy"))
                    .flatMap(grant -> postPolicy(policy, grant))
                    .map(locationHeaderValue ->
                            new ResponseEntity<PolicyObjectInformation>(policyObjectInfo,helper.createHttpHeaders(
                                    "location",helper.buildURI(policy.getId(), serverWebExchange)), HttpStatus.CREATED))
                    .doOnError(errorHandlingService::handleError));
        } catch (Exception ex) {
            return Mono.error(ex);
        }

    }

    private Mono<String> postPolicy(Policy policy, Lock.Grant grant) {
        return  helper.checkRicStateIdle(policy.getRic())
                .doOnError(errorHandlingService::handleError)
                .flatMap(ric -> helper.checkSupportedType(ric, policy.getType()))
                .doOnError(errorHandlingService::handleError)
                .flatMap(a1ClientFactory::createA1Client)
                .flatMap(a1Client -> a1Client.putPolicy(policy))
                .doOnError(errorHandlingService::handleError)
                .doOnNext(policyString -> policies.put(policy))
                .doFinally(releaseLock -> grant.unlockBlocking())
                .doOnError(errorHandlingService::handleError);
    }

    public Mono<ResponseEntity<Object>> putPolicyService(String policyId, Object body, ServerWebExchange exchange) {
        try {
            Policy existingPolicy = policies.getPolicy(policyId);
            PolicyObjectInformation pos =
                    new PolicyObjectInformation(existingPolicy.getRic().getConfig().getRicId(), body, existingPolicy.getType().getId());
            Policy updatedPolicy = helper.buildPolicy(pos, existingPolicy.getType(), existingPolicy.getRic(), policyId);
            Ric ric = existingPolicy.getRic();
            return authorizationService.authCheck(exchange, updatedPolicy, AccessType.WRITE)
                    .doOnError(errorHandlingService::handleError)
                    .flatMap(policy -> ric.getLock().lock(Lock.LockType.SHARED, "updatePolicy"))
                    .doOnError(errorHandlingService::handleError)
                    .flatMap(grant -> postPolicy(updatedPolicy, grant))
                    .map(header -> new ResponseEntity<Object>(policies.get(updatedPolicy.getId()).getJson(), HttpStatus.OK))
                    .doOnError(errorHandlingService::handleError);
        } catch(Exception ex) {
            return Mono.error(ex);
        }
    }

    public Mono<ResponseEntity<Flux<PolicyTypeInformation>>> getPolicyTypesService(String nearRtRicId, String typeName,
                                                                                   String compatibleWithVersion) throws ServiceException {
        if (compatibleWithVersion != null && typeName == null) {
            throw new ServiceException("Parameter " + Consts.COMPATIBLE_WITH_VERSION_PARAM + " can only be used when "
                    + Consts.TYPE_NAME_PARAM + " is given", HttpStatus.BAD_REQUEST);
        }
        Collection<PolicyTypeInformation> listOfPolicyTypes = new ArrayList<>();
        if (nearRtRicId == null || nearRtRicId.isEmpty() || nearRtRicId.isBlank()) {
            for(Ric ric : rics.getRics()) {
                Collection<PolicyType> filteredPolicyTypes = PolicyTypes.filterTypes(ric.getSupportedPolicyTypes(), typeName,
                        compatibleWithVersion);
                listOfPolicyTypes.addAll(helper.toPolicyTypeInfoCollection(filteredPolicyTypes, ric));
            }
        } else {
            Ric ric = rics.get(nearRtRicId);
            if (ric == null)
                throw new EntityNotFoundException("Near-RT RIC not Found using ID: " +nearRtRicId);
            Collection<PolicyType> filteredPolicyTypes = PolicyTypes.filterTypes(ric.getSupportedPolicyTypes(), typeName,
                    compatibleWithVersion);
            listOfPolicyTypes.addAll(helper.toPolicyTypeInfoCollection(filteredPolicyTypes, ric));
        }
        return Mono.just(new ResponseEntity<>(Flux.fromIterable(listOfPolicyTypes), HttpStatus.OK));
    }

    public Mono<ResponseEntity<Flux<PolicyInformation>>> getPolicyIdsService(String policyTypeId, String nearRtRicId,
                                                                             String serviceId, String typeName,
                                                                             ServerWebExchange exchange) throws EntityNotFoundException {
        if ((policyTypeId != null && this.policyTypes.get(policyTypeId) == null))
            throw new EntityNotFoundException("Policy type not found using ID: " +policyTypeId);
        if ((nearRtRicId != null && this.rics.get(nearRtRicId) == null))
            throw new EntityNotFoundException("Near-RT RIC not found using ID: " +nearRtRicId);

        Collection<Policy> filtered = policies.filterPolicies(policyTypeId, nearRtRicId, serviceId, typeName);
        return Flux.fromIterable(filtered)
                .flatMap(policy -> authorizationService.authCheck(exchange, policy, AccessType.READ))
                .onErrorContinue((error,item) -> logger.warn("Error occurred during authorization check for " +
                        "policy {}: {}", item, error.getMessage()))
                .collectList()
                .map(authPolicies -> new ResponseEntity<>(helper.toFluxPolicyInformation(authPolicies), HttpStatus.OK))
                .doOnError(error -> logger.error(error.getMessage()));
    }

    public Mono<ResponseEntity<Object>> getPolicyService(String policyId, ServerWebExchange serverWebExchange)
            throws EntityNotFoundException{
            Policy policy = policies.getPolicy(policyId);
        return authorizationService.authCheck(serverWebExchange, policy, AccessType.READ)
                .map(x -> new ResponseEntity<Object>(policy.getJson(), HttpStatus.OK))
                .doOnError(errorHandlingService::handleError);
    }

    public Mono<ResponseEntity<PolicyTypeObject>> getPolicyTypeDefinitionService(String policyTypeId)
            throws EntityNotFoundException{
        PolicyType singlePolicyType = policyTypes.get(policyTypeId);
        if (singlePolicyType == null)
            throw new EntityNotFoundException("PolicyType not found with ID: " + policyTypeId);
        return Mono.just(new ResponseEntity<PolicyTypeObject>(new PolicyTypeObject(singlePolicyType.getSchema()), HttpStatus.OK));
    }

    public Mono<ResponseEntity<Void>> deletePolicyService(String policyId, ServerWebExchange serverWebExchange)
            throws EntityNotFoundException {
        Policy singlePolicy = policies.getPolicy(policyId);
        return authorizationService.authCheck(serverWebExchange, singlePolicy, AccessType.WRITE)
                .doOnError(errorHandlingService::handleError)
                .flatMap(policy -> policy.getRic().getLock().lock(Lock.LockType.SHARED, "deletePolicy"))
                .flatMap(grant -> deletePolicy(singlePolicy, grant))
                .doOnError(errorHandlingService::handleError);
    }

    private Mono<ResponseEntity<Void>> deletePolicy(Policy policy, Lock.Grant grant) {
        return  helper.checkRicStateIdle(policy.getRic())
                .doOnError(errorHandlingService::handleError)
                .flatMap(ric -> helper.checkSupportedType(ric, policy.getType()))
                .doOnError(errorHandlingService::handleError)
                .flatMap(a1ClientFactory::createA1Client)
                .doOnError(errorHandlingService::handleError)
                .flatMap(a1Client -> a1Client.deletePolicy(policy))
                .doOnError(errorHandlingService::handleError)
                .doOnNext(policyString -> policies.remove(policy))
                .doFinally(releaseLock -> grant.unlockBlocking())
                .map(successResponse -> new ResponseEntity<Void>(HttpStatus.NO_CONTENT))
                .doOnError(errorHandlingService::handleError);
    }

    private Mono<String> getStatus(Policy policy, Lock.Grant grant) {
        return  helper.checkRicStateIdle(policy.getRic())
                .doOnError(errorHandlingService::handleError)
                .flatMap(a1ClientFactory::createA1Client)
                .flatMap(a1Client -> a1Client.getPolicyStatus(policy))
                .doOnError(errorHandlingService::handleError)
                .doFinally(releaseLock -> grant.unlockBlocking())
                .doOnError(errorHandlingService::handleError);
    }

    public Mono<ResponseEntity<Object>> getPolicyStatus(String policyId, ServerWebExchange exchange) throws Exception {
        Policy policy = policies.getPolicy(policyId);

        return authorizationService.authCheck(exchange, policy, AccessType.READ)
                .doOnError(errorHandlingService::handleError)
                .flatMap(policyLock -> policy.getRic().getLock().lock(Lock.LockType.SHARED, "getStatus"))
                .doOnError(errorHandlingService::handleError)
                .flatMap(grant -> getStatus(policy, grant))
                .doOnError(errorHandlingService::handleError)
                .map(successResponse -> new ResponseEntity<Object>(successResponse, HttpStatus.OK))
                .doOnError(errorHandlingService::handleError);
    }
}
