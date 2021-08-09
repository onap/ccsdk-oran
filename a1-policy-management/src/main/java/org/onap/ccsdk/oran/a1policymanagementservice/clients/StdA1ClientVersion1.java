/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
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

import java.util.Arrays;
import java.util.List;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for accessing standard A1 REST API version 1.1
 */
public class StdA1ClientVersion1 implements A1Client {

    public static class UriBuilder implements A1UriBuilder {

        private final RicConfig ricConfig;

        public UriBuilder(RicConfig ricConfig) {
            this.ricConfig = ricConfig;
        }

        /**
         * /A1-P/v1/policies/{policyId}
         */
        @Override
        public String createPutPolicyUri(String type, String policyId, String statusNotificationUri) {
            return policiesBaseUri() + policyId;
        }

        /**
         * /A1-P/v1/policies
         */
        @Override
        public String createGetPolicyIdsUri(String type) {
            return baseUri() + "/policies";
        }

        /**
         * /A1-P/v1/policies/{policyId}
         */
        @Override
        public String createDeleteUri(String type, String policyId) {
            return policiesBaseUri() + policyId;
        }

        /**
         * /A1-P/v1/policies/{policyId}/status
         */
        @Override
        public String createGetPolicyStatusUri(String type, String policyId) {
            return policiesBaseUri() + policyId + "/status";
        }

        private String baseUri() {
            return ricConfig.baseUrl() + "/A1-P/v1";
        }

        private String policiesBaseUri() {
            return createGetPolicyIdsUri("") + "/";
        }

        @Override
        public String createPolicyTypesUri() {
            throw new NullPointerException("Not supported URI");
        }

        @Override
        public String createGetSchemaUri(String type) {
            throw new NullPointerException("Not supported URI");
        }
    }

    private final AsyncRestClient restClient;
    private final UriBuilder uri;

    public StdA1ClientVersion1(RicConfig ricConfig, AsyncRestClientFactory restClientFactory) {
        this(restClientFactory.createRestClientUseHttpProxy(""), ricConfig);
    }

    public StdA1ClientVersion1(AsyncRestClient restClient, RicConfig ricConfig) {
        this.restClient = restClient;
        this.uri = new UriBuilder(ricConfig);
    }

    @Override
    public Mono<List<String>> getPolicyIdentities() {
        return getPolicyIds() //
                .collectList();
    }

    @Override
    public Mono<String> putPolicy(Policy policy) {
        return restClient.put(
                uri.createPutPolicyUri(policy.getType().getId(), policy.getId(), policy.getStatusNotificationUri()),
                policy.getJson());
    }

    @Override
    public Mono<List<String>> getPolicyTypeIdentities() {
        return Mono.just(Arrays.asList(""));
    }

    @Override
    public Mono<String> getPolicyTypeSchema(String policyTypeId) {
        return Mono.just("{}");
    }

    @Override
    public Mono<String> deletePolicy(Policy policy) {
        return deletePolicyById(policy.getId());
    }

    @Override
    public Flux<String> deleteAllPolicies() {
        return getPolicyIds() //
                .flatMap(this::deletePolicyById); //
    }

    @Override
    public Mono<A1ProtocolType> getProtocolVersion() {
        return getPolicyIdentities() //
                .flatMap(x -> Mono.just(A1ProtocolType.STD_V1_1));
    }

    @Override
    public Mono<String> getPolicyStatus(Policy policy) {
        return restClient.get(uri.createGetPolicyStatusUri(policy.getType().getId(), policy.getId()));
    }

    private Flux<String> getPolicyIds() {
        return restClient.get(uri.createGetPolicyIdsUri("")) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Mono<String> deletePolicyById(String policyId) {
        return restClient.delete(uri.createDeleteUri("", policyId));
    }
}
