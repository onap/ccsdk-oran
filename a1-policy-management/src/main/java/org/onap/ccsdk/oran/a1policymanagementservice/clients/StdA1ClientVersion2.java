/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.json.JSONObject;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for accessing ORAN A1-P Vesion 2.0 REST API
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class StdA1ClientVersion2 implements A1Client {
    static final int CONCURRENCY_RIC = 1; // How many paralell requests that is sent to one NearRT RIC

    public static class OranV2UriBuilder implements A1UriBuilder {
        private final RicConfig ricConfig;

        public OranV2UriBuilder(RicConfig ricConfig) {
            this.ricConfig = ricConfig;
        }

        @Override
        public String createPutPolicyUri(String type, String policyId, String statusNotificationUri) {
            String policyUri = createPolicyUri(type, policyId);
            if (statusNotificationUri.isEmpty()) {
                return policyUri;
            }
            UriBuilderFactory builderFactory = new DefaultUriBuilderFactory(policyUri);
            return builderFactory.builder() //
                    .queryParam("notificationDestination", statusNotificationUri) //
                    .build() //
                    .normalize() //
                    .toASCIIString();
        }

        /**
         * /A1-P/v2​/policytypes/{policy_type_id}/policies
         */
        @Override
        public String createGetPolicyIdsUri(String type) {
            return createPolicyTypeUri(type) + "/policies";
        }

        @Override
        public String createDeleteUri(String type, String policyId) {
            return createPolicyUri(type, policyId);
        }

        /**
         * ​/A1-P/v2​/policytypes​/{policy_type_id}​/policies​/{policy_instance_id}​/status
         */
        @Override
        public String createGetPolicyStatusUri(String type, String policyId) {
            return createPolicyUri(type, policyId) + "/status";
        }

        /**
         * /A1-P/v2/policytypes/{policy_type_id}
         */
        @Override
        public String createGetSchemaUri(String type) {
            return this.createPolicyTypeUri(type);
        }

        /**
         * ​/A1-P/v2​/policytypes​/{policy_type_id}
         */
        @Override
        public String createPolicyTypesUri() {
            return baseUri() + "/policytypes";
        }

        /**
         * ​/A1-P/v2​/policytypes​/{policy_type_id}​/policies​/{policy_instance_id}
         */
        private String createPolicyUri(String type, String id) {
            return createPolicyTypeUri(type) + "/policies/" + id;
        }

        /**
         * /A1-P/v2/policytypes/{policy_type_id}
         */
        private String createPolicyTypeUri(String type) {
            return createPolicyTypesUri() + "/" + type;
        }

        private String baseUri() {
            return ricConfig.baseUrl() + "/A1-P/v2";
        }
    }

    private static final String TITLE = "title";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AsyncRestClient restClient;
    private final OranV2UriBuilder uriBuiler;

    public StdA1ClientVersion2(RicConfig ricConfig, AsyncRestClientFactory restClientFactory) {
        this(ricConfig, restClientFactory.createRestClientUseHttpProxy(""));
    }

    public StdA1ClientVersion2(RicConfig ricConfig, AsyncRestClient restClient) {
        this.restClient = restClient;
        logger.debug("OscA1Client for ric: {}", ricConfig.ricId());

        uriBuiler = new OranV2UriBuilder(ricConfig);
    }

    public static Mono<String> extractPolicySchema(String policyTypeResponse, String policyTypeId) {
        try {
            JSONObject obj = new JSONObject(policyTypeResponse);
            JSONObject schemaObj = obj.getJSONObject("policySchema");
            schemaObj.put(TITLE, policyTypeId);
            return Mono.just(schemaObj.toString());
        } catch (Exception e) {
            String exceptionString = e.toString();
            logger.error("Unexpected response for policy type: {}, exception: {}", policyTypeResponse, exceptionString);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<List<String>> getPolicyTypeIdentities() {
        return getPolicyTypeIds() //
                .collectList();
    }

    @Override
    public Mono<List<String>> getPolicyIdentities() {
        return getPolicyTypeIds() //
                .flatMap(this::getPolicyIdentitiesByType) //
                .collectList();
    }

    @Override
    public Mono<String> getPolicyTypeSchema(String policyTypeId) {
        String schemaUri = uriBuiler.createGetSchemaUri(policyTypeId);
        return restClient.get(schemaUri) //
                .flatMap(response -> extractPolicySchema(response, policyTypeId));
    }

    @Override
    public Mono<String> putPolicy(Policy policy) {
        String policyUri = this.uriBuiler.createPutPolicyUri(policy.getType().getId(), policy.getId(),
                policy.getStatusNotificationUri());
        return restClient.put(policyUri, policy.getJson());
    }

    @Override
    public Mono<String> deletePolicy(Policy policy) {
        return deletePolicyById(policy.getType().getId(), policy.getId());
    }

    @Override
    public Mono<A1ProtocolType> getProtocolVersion() {
        return restClient.get(uriBuiler.createPolicyTypesUri()) //
                .flatMap(notUsed -> Mono.just(A1ProtocolType.STD_V2_0_0));
    }

    @Override
    public Flux<String> deleteAllPolicies() {
        return getPolicyTypeIds() //
                .flatMap(this::deletePoliciesForType, CONCURRENCY_RIC);
    }

    @Override
    public Mono<String> getPolicyStatus(Policy policy) {
        String statusUri = uriBuiler.createGetPolicyStatusUri(policy.getType().getId(), policy.getId());
        return restClient.get(statusUri);

    }

    private Flux<String> getPolicyTypeIds() {
        return restClient.get(uriBuiler.createPolicyTypesUri()) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Flux<String> getPolicyIdentitiesByType(String typeId) {
        return restClient.get(uriBuiler.createGetPolicyIdsUri(typeId)) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Mono<String> deletePolicyById(String typeId, String policyId) {
        String policyUri = uriBuiler.createDeleteUri(typeId, policyId);
        return restClient.delete(policyUri);
    }

    private Flux<String> deletePoliciesForType(String typeId) {
        return getPolicyIdentitiesByType(typeId) //
                .flatMap(policyId -> deletePolicyById(typeId, policyId), CONCURRENCY_RIC);
    }
}
