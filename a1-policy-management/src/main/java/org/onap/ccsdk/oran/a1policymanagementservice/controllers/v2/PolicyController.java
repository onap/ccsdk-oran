/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2023 Nordix Foundation. All rights reserved.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;

import lombok.RequiredArgsConstructor;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v2.A1PolicyManagementApi;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.AuthorizationCheck;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest.Input.AccessType;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyTypeDefinition;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyTypeIdList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfoList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyIdList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyStatusInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController("policyControllerV2")
@RequiredArgsConstructor
@Tag(//
        name = PolicyController.API_NAME, //
        description = PolicyController.API_DESCRIPTION //
)
public class PolicyController implements A1PolicyManagementApi {

    public static final String API_NAME = "A1 Policy Management";
    public static final String API_DESCRIPTION = "";
    public static class RejectionException extends Exception {
        private static final long serialVersionUID = 1L;

        @Getter
        private final HttpStatus status;

        public RejectionException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }
    }

    private final Rics rics;
    private final PolicyTypes policyTypes;
    private final Policies policies;
    private final A1ClientFactory a1ClientFactory;
    private final Services services;
    private final ObjectMapper objectMapper;
    private final AuthorizationCheck authorization;

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Gson gson = new GsonBuilder().create();

    @Override
    public Mono<ResponseEntity<PolicyTypeDefinition>> getPolicyTypeDefinition(String policyTypeId, ServerWebExchange exchange)
            throws EntityNotFoundException, JsonProcessingException {
        PolicyType type = policyTypes.getType(policyTypeId);
        JsonNode node = objectMapper.readTree(type.getSchema());
        PolicyTypeDefinition policyTypeDefinition = new PolicyTypeDefinition().policySchema(node);
        return Mono.just(new ResponseEntity<>(policyTypeDefinition, HttpStatus.OK));
    }

    @Override
    public Mono<ResponseEntity<PolicyTypeIdList>> getPolicyTypes(String ricId, String typeName, String compatibleWithVersion, ServerWebExchange exchange) throws Exception {
        if (compatibleWithVersion != null && typeName == null) {
            throw new ServiceException("Parameter " + Consts.COMPATIBLE_WITH_VERSION_PARAM + " can only be used when "
                    + Consts.TYPE_NAME_PARAM + " is given", HttpStatus.BAD_REQUEST);
        }

        Collection<PolicyType> types =
                ricId != null ? rics.getRic(ricId).getSupportedPolicyTypes() : this.policyTypes.getAll();

        types = PolicyTypes.filterTypes(types, typeName, compatibleWithVersion);
        return Mono.just(new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK));
    }


    @Override
    public Mono<ResponseEntity<PolicyInfo>> getPolicy(String policyId, final ServerWebExchange exchange)
            throws EntityNotFoundException {
        Policy policy = policies.getPolicy(policyId);
        return authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.READ) //
                .map(x -> new ResponseEntity<>(toPolicyInfo(policy), HttpStatus.OK)) //
                .doOnError(error -> logger.error(error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<Object>> deletePolicy(String policyId, ServerWebExchange exchange) throws Exception {

        Policy policy = policies.getPolicy(policyId);
        keepServiceAlive(policy.getOwnerServiceId());

        return authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.WRITE)
                .flatMap(x -> policy.getRic().getLock().lock(Lock.LockType.SHARED, "deletePolicy"))
                .flatMap(grant -> deletePolicy(grant, policy))
                .onErrorResume(this::handleException);
    }

    Mono<ResponseEntity<Object>> deletePolicy(Lock.Grant grant, Policy policy) {
        return checkRicStateIdle(policy.getRic()) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(policy.getRic()))
                .doOnNext(notUsed -> policies.remove(policy))
                .doFinally(x -> grant.unlockBlocking())
                .flatMap(client -> client.deletePolicy(policy))
                .map(notUsed -> new ResponseEntity<>(HttpStatus.NO_CONTENT))
                .onErrorResume(this::handleException);
    }

    @Override
    public Mono<ResponseEntity<Object>> putPolicy(final Mono<PolicyInfo> policyInfo, final ServerWebExchange exchange) {

        return policyInfo.flatMap(policyInfoValue -> {
                String jsonString  = gson.toJson(policyInfoValue.getPolicyData());
            return Mono.zip(
                            Mono.justOrEmpty(rics.get(policyInfoValue.getRicId()))
                                    .switchIfEmpty(Mono.error(new EntityNotFoundException("Near-RT RIC not found"))),
                            Mono.justOrEmpty(policyTypes.get(policyInfoValue.getPolicytypeId()))
                                    .switchIfEmpty(Mono.error(new EntityNotFoundException("policy type not found")))
                    )
                    .flatMap(tuple -> {
                        Ric ric = tuple.getT1();
                        PolicyType type = tuple.getT2();
                        keepServiceAlive(policyInfoValue.getServiceId());
                        Policy policy = Policy.builder()
                                .id(policyInfoValue.getPolicyId())
                                .json(jsonString)
                                .type(type)
                                .ric(ric)
                                .ownerServiceId(policyInfoValue.getServiceId())
                                .lastModified(Instant.now())
                                .isTransient(policyInfoValue.getTransient())
                                .statusNotificationUri(policyInfoValue.getStatusNotificationUri() == null ? "" : policyInfoValue.getStatusNotificationUri())
                                .build();

                        return authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.WRITE)
                                .flatMap(x -> ric.getLock().lock(Lock.LockType.SHARED, "putPolicy"))
                                .flatMap(grant -> putPolicy(grant, policy));
                    })
                    .onErrorResume(this::handleException);
        });
    }



    private Mono<ResponseEntity<Object>> putPolicy(Lock.Grant grant, Policy policy) {
        final boolean isCreate = this.policies.get(policy.getId()) == null;
        final Ric ric = policy.getRic();

        return checkRicStateIdle(ric) //
                .flatMap(notUsed -> checkSupportedType(ric, policy.getType())) //
                .flatMap(notUsed -> validateModifiedPolicy(policy)) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(ric)) //
                .flatMap(client -> client.putPolicy(policy)) //
                .doOnNext(notUsed -> policies.put(policy)) //
                .doFinally(x -> grant.unlockBlocking()) //
                .flatMap(notUsed -> Mono.just(new ResponseEntity<>(isCreate ? HttpStatus.CREATED : HttpStatus.OK))) //
                .onErrorResume(this::handleException);

    }

    private Mono<ResponseEntity<Object>> handleException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            return ErrorResponse.createMono(e.getResponseBodyAsString(), e.getStatusCode());
        } else if (throwable instanceof WebClientException) {
            WebClientException e = (WebClientException) throwable;
            return ErrorResponse.createMono(e.getMessage(), HttpStatus.BAD_GATEWAY);
        } else if (throwable instanceof RejectionException) {
            RejectionException e = (RejectionException) throwable;
            return ErrorResponse.createMono(e.getMessage(), e.getStatus());
        } else if (throwable instanceof ServiceException) {
            ServiceException e = (ServiceException) throwable;
            return ErrorResponse.createMono(e.getMessage(), e.getHttpStatus());
        } else {
            return ErrorResponse.createMono(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Object> validateModifiedPolicy(Policy policy) {
        // Check that ric is not updated
        Policy current = this.policies.get(policy.getId());
        if (current != null && !current.getRic().id().equals(policy.getRic().id())) {
            RejectionException e = new RejectionException("Policy cannot change RIC, policyId: " + current.getId() + //
                    ", RIC ID: " + current.getRic().id() + //
                    ", new ID: " + policy.getRic().id(), HttpStatus.CONFLICT);
            logger.debug("Request rejected, {}", e.getMessage());
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private Mono<Object> checkSupportedType(Ric ric, PolicyType type) {
        if (!ric.isSupportingType(type.getId())) {
            logger.debug("Request rejected, type not supported, RIC: {}", ric);
            RejectionException e = new RejectionException(
                    "Type: " + type.getId() + " not supported by RIC: " + ric.id(), HttpStatus.NOT_FOUND);
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private Mono<Object> checkRicStateIdle(Ric ric) {
        if (ric.getState() == Ric.RicState.AVAILABLE) {
            return Mono.just("{}");
        } else {
            logger.debug("Request rejected Near-RT RIC not IDLE, ric: {}", ric);
            RejectionException e = new RejectionException(
                    "Near-RT RIC: is not operational, id: " + ric.id() + ", state: " + ric.getState(),
                    HttpStatus.LOCKED);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<ResponseEntity<PolicyInfoList>> getPolicyInstances(String policyTypeId, String ricId, String serviceId, String typeName, ServerWebExchange exchange) throws Exception {
        if ((policyTypeId != null && this.policyTypes.get(policyTypeId) == null)) {
            throw new EntityNotFoundException("Policy type identity not found");
        }
        if ((ricId != null && this.rics.get(ricId) == null)) {
            throw new EntityNotFoundException("Near-RT RIC not found");
        }

        Collection<Policy> filtered = policies.filterPolicies(policyTypeId, ricId, serviceId, typeName);
        return Flux.fromIterable(filtered) //
                .flatMap(policy -> authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.READ))
                .doOnError(e -> logger.debug("Unauthorized to read policy: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .collectList()
                .map(authPolicies -> new ResponseEntity<>(policiesToJson(authPolicies), HttpStatus.OK))
                .doOnError(error -> logger.error(error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<PolicyIdList>> getPolicyIds(String policyTypeId, String ricId, String serviceId, String typeName, ServerWebExchange exchange) throws Exception {
        if ((policyTypeId != null && this.policyTypes.get(policyTypeId) == null)) {
            throw new EntityNotFoundException("Policy type not found");
        }
        if ((ricId != null && this.rics.get(ricId) == null)) {
            throw new EntityNotFoundException("Near-RT RIC not found");
        }

        Collection<Policy> filtered = policies.filterPolicies(policyTypeId, ricId, serviceId, typeName);
        return Flux.fromIterable(filtered)
                .flatMap(policy -> authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.READ))
                .doOnError(e -> logger.debug("Unauthorized to read policy: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .collectList()
                .map(authPolicies -> new ResponseEntity<>(toPolicyIdsJson(authPolicies), HttpStatus.OK))
                .doOnError(error -> logger.error(error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<PolicyStatusInfo>> getPolicyStatus(String policyId, ServerWebExchange exchange) throws Exception {
        Policy policy = policies.getPolicy(policyId);

        return authorization.doAccessControl(exchange.getRequest().getHeaders().toSingleValueMap(), policy, AccessType.READ) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(policy.getRic())) //
                .flatMap(client -> client.getPolicyStatus(policy).onErrorResume(e -> Mono.just("{}"))) //
                .flatMap(status -> createPolicyStatus(policy, status))
                .doOnError(error -> logger.error(error.getMessage()));
    }

    private Mono<ResponseEntity<PolicyStatusInfo>> createPolicyStatus(Policy policy, String statusFromNearRic) {

        PolicyStatusInfo policyStatusInfo = new PolicyStatusInfo();
        policyStatusInfo.setLastModified(policy.getLastModified().toString());
        policyStatusInfo.setStatus(fromJson(statusFromNearRic));
        return Mono.just(new ResponseEntity<>(policyStatusInfo, HttpStatus.OK));
    }

    private void keepServiceAlive(String name) {
        Service s = this.services.get(name);
        if (s != null) {
            s.keepAlive();
        }
    }

    private PolicyInfo toPolicyInfo(Policy policy) {
       try {
           PolicyInfo policyInfo = new PolicyInfo()
                   .policyId(policy.getId())
                   .policyData(objectMapper.readTree(policy.getJson()))
                   .ricId(policy.getRic().id())
                   .policytypeId(policy.getType().getId())
                   .serviceId(policy.getOwnerServiceId())
                   ._transient(policy.isTransient());
           if (!policy.getStatusNotificationUri().isEmpty()) {
               policyInfo.setStatusNotificationUri(policy.getStatusNotificationUri());
           }
           return policyInfo;
       } catch (JsonProcessingException ex) {
           throw new RuntimeException(ex);
       }
    }

    private PolicyInfoList policiesToJson(Collection<Policy> policies) {

                List<PolicyInfo> policiesList = new ArrayList<>(policies.size());
                PolicyInfoList policyInfoList = new PolicyInfoList();
                for (Policy policy : policies) {
                    policiesList.add(toPolicyInfo(policy));
                }
                policyInfoList.setPolicies(policiesList);
                return policyInfoList;
    }

    private Object fromJson(String jsonStr) {
        return gson.fromJson(jsonStr, Object.class);
    }

    private PolicyTypeIdList toPolicyTypeIdsJson(Collection<PolicyType> policyTypes) {

        List<String> policyTypeList = new ArrayList<>(policyTypes.size());
        PolicyTypeIdList idList = new PolicyTypeIdList();
        for (PolicyType policyType : policyTypes) {
            policyTypeList.add(policyType.getId());
        }
        idList.setPolicytypeIds(policyTypeList);
        return idList;
    }

    private PolicyIdList toPolicyIdsJson(Collection<Policy> policies) {

            List<String> policyIds = new ArrayList<>(policies.size());
            PolicyIdList idList = new PolicyIdList();
            for (Policy policy : policies) {
                policyIds.add(policy.getId());
            }
            idList.setPolicyIds(policyIds);
            return idList;
    }
}
