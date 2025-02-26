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

package org.onap.ccsdk.oran.a1policymanagementservice.util.v3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.InvalidRequestException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyTypeInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.*;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Helper {

    @Autowired
    private TokenService tokenService;

    private final Services services;

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Gson gson = new GsonBuilder().create();

    public void keepServiceAlive(String name) {
        Service s = this.services.get(name);
        if (s != null) {
            s.keepAlive();
        }
    }

    public Mono<Ric> checkRicStateIdle(Ric ric) {
        if (ric.getState() == Ric.RicState.AVAILABLE) {
            return Mono.just(ric);
        } else {
            logger.debug("Request rejected Near-RT RIC not IDLE, ric: {}", ric);
            ServiceException e = new ServiceException(
                    "Near-RT RIC: is not operational, id: " + ric.id() + ", state: " + ric.getState(),
                    HttpStatus.LOCKED);
            return Mono.error(e);
        }
    }

    public Mono<Ric> checkSupportedType(Ric ric, PolicyType type) {
        if (!ric.isSupportingType(type.getId())) {
            logger.debug("Request rejected, type not supported, RIC: {}", ric);
            ServiceException e = new ServiceException(
                    "Type: " + type.getId() + " not supported by RIC: " + ric.id(), HttpStatus.BAD_REQUEST);
            return Mono.error(e);
        }
        return Mono.just(ric);
    }

    public Policy buildPolicy(PolicyObjectInformation policyObjectInformation, PolicyType policyType, Ric ric, String policyId, ServerWebExchange exchange) {
        return Policy.builder()
                .id(policyId)
                .json(toJson(policyObjectInformation.getPolicyObject()))
                .type(policyType)
                .ric(ric)
                .ownerServiceId(tokenService.getServiceId(policyObjectInformation, exchange))
                .lastModified(Instant.now())
                .isTransient(policyObjectInformation.getTransient())
                .build();
    }

    public Boolean jsonSchemaValidation(Object jsonObject) {
        toJson(jsonObject);
        return true;
    }

    public String policyIdGeneration(PolicyObjectInformation policyObjectInfo) {
        if (policyObjectInfo.getPolicyId() == null || policyObjectInfo.getPolicyId().isEmpty() ||
                policyObjectInfo.getPolicyId().isBlank())
            return UUID.randomUUID().toString();
        else
            return policyObjectInfo.getPolicyId().trim();
    }

    public String toJson(Object jsonObject) {
        return gson.toJson(jsonObject);
    }

    public HttpHeaders createHttpHeaders(String headerName, String headerValue) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(headerName, headerValue);
        return httpHeaders;
    }

    public String buildURI(String policyId, ServerWebExchange serverWebExchange) {
        return UriComponentsBuilder.fromUri(serverWebExchange.getRequest().getURI())
                .path("/{id}")
                .buildAndExpand(policyId)
                .toString();
    }

    public Mono<Policy> isPolicyAlreadyCreated(Policy policy, Policies policies) {
        if (policies.get(policy.getId()) != null) {
            return Mono.error(new ServiceException
                    ("Policy already created with ID: " + policy.getId(), HttpStatus.CONFLICT));
        }
            return Mono.just(policy);
    }

    public Flux<PolicyInformation> toFluxPolicyInformation(Collection<Policy> policyCollection) {
        return Flux.fromIterable(policyCollection.stream()
                .map(singlePolicy -> new PolicyInformation(singlePolicy.getId(), singlePolicy.getRic().getConfig().getRicId()))
                .collect(Collectors.toList()));
    }

    public Collection<PolicyTypeInformation> toPolicyTypeInfoCollection(Collection<PolicyType> policyTypeCollection, Ric ric) {
        return policyTypeCollection.stream()
                .map(type -> new PolicyTypeInformation(type.getId(), ric.getConfig().getRicId()))
                .collect(Collectors.toList());
    }


    public void validateQueryParameters(ServerWebExchange exchange, Set<String> allowedParams) throws InvalidRequestException {
        Set<String> receivedParams = exchange.getRequest().getQueryParams().keySet();

        Set<String> extraParams = receivedParams.stream()
                .filter(param -> !allowedParams.contains(param))
                .collect(Collectors.toSet());

        if (!extraParams.isEmpty()) {
            throw new InvalidRequestException("Unexpected query parameters: " + extraParams);
        }
    }
}
