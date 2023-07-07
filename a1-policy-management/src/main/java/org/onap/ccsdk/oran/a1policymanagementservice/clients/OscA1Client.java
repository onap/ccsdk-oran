/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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
import java.util.Set;

import org.json.JSONObject;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.apache.http.client.utils.URIBuilder;
/**
 * Client for accessing OSC A1 REST API
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class OscA1Client implements A1Client {
    static final int CONCURRENCY_RIC = 1; // How many paralell requests that is sent to one NearRT RIC

    public static class UriBuilder implements A1UriBuilder {
        private final RicConfig ricConfig;

        public UriBuilder(RicConfig ricConfig) {
            this.ricConfig = ricConfig;
        }

        @Override
        public String createPutPolicyUri(String type, String policyId, String notificationDestinationUri) {
	    return createPolicyUri(type, policyId, notificationDestinationUri);
        }

        /**
         * /a1-p/policytypes/{policy_type_id}/policies
         */
        @Override
        public String createGetPolicyIdsUri(String type) {
            return createPolicyTypeUri(type) + "/policies";
        }

        @Override
        public String createDeleteUri(String type, String policyId) {
	    return createPolicyUri(type, policyId, null);
        }

        /**
         * ​/a1-p​/policytypes​/{policy_type_id}​/policies​/{policy_instance_id}​/status
         */
        @Override
        public String createGetPolicyStatusUri(String type, String policyId) {
	    return createPolicyUri(type, policyId, null) + "/status";
        }

        /**
         * ​/a1-p​/healthcheck
         */
        public String createHealtcheckUri() {
            return baseUri() + "/healthcheck";
        }

        /**
         * /a1-p/policytypes/{policy_type_id}
         */
        @Override
        public String createGetSchemaUri(String type) {
            return this.createPolicyTypeUri(type);
        }

        /**
         * ​/a1-p​/policytypes​/{policy_type_id}
         */
        @Override
        public String createPolicyTypesUri() {
            return baseUri() + "/policytypes";
        }

        /**
         * ​/a1-p​/policytypes​/{policy_type_id}​/policies​/{policy_instance_id}
         */
	    private String createPolicyUri(String type, String id, String notificationDestination) {
               String url = "";
               URIBuilder ub = null;
               try {
                    ub = new URIBuilder(createPolicyTypeUri(type) + "/policies/" + id);
                    if(notificationDestination != null) {
                       ub.addParameter("notificationDestination", notificationDestination);
                    }
                    url = ub.toString();
               }
               catch(Exception e) {
                    String exceptionString = e.toString();
                    logger.error("Unexpected error in policy URI creation for policy type: {}, exception: {}", type, exceptionString);
               }
               return url;
         }

        /**
         * /a1-p/policytypes/{policy_type_id}
         */
        private String createPolicyTypeUri(String type) {
            return createPolicyTypesUri() + "/" + type;
        }

        private String baseUri() {
            return ricConfig.getBaseUrl() + "/a1-p";
        }
    }

    private static final String TITLE = "title";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AsyncRestClient restClient;
    private final UriBuilder uri;

    public OscA1Client(RicConfig ricConfig, AsyncRestClientFactory restClientFactory) {
        this(ricConfig, restClientFactory.createRestClientUseHttpProxy(""));
    }

    public OscA1Client(RicConfig ricConfig, AsyncRestClient restClient) {
        this.restClient = restClient;
        uri = new UriBuilder(ricConfig);
        logger.debug("A1Client ("+getClass().getTypeName()+") created for ric: {}", ricConfig.getRicId());
    }

    public static Mono<String> extractCreateSchema(String policyTypeResponse, String policyTypeId) {
        try {
            JSONObject obj = new JSONObject(policyTypeResponse);
            JSONObject schemaObj = obj.getJSONObject("create_schema");
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
        String schemaUri = uri.createGetSchemaUri(policyTypeId);
        return restClient.get(schemaUri) //
                .flatMap(response -> extractCreateSchema(response, policyTypeId));
    }

    @Override
    public Mono<String> putPolicy(Policy policy) {
        String policyUri = this.uri.createPutPolicyUri(policy.getType().getId(), policy.getId(),
                policy.getStatusNotificationUri());
        return restClient.put(policyUri, policy.getJson());
    }

    @Override
    public Mono<String> deletePolicy(Policy policy) {
        return deletePolicyById(policy.getType().getId(), policy.getId());
    }

    @Override
    public Mono<A1ProtocolType> getProtocolVersion() {
        return restClient.get(uri.createHealtcheckUri()) //
                .flatMap(notUsed -> Mono.just(A1ProtocolType.OSC_V1));
    }

    @Override
    public Flux<String> deleteAllPolicies(Set<String> excludePolicyIds) {
        return getPolicyTypeIds() //
                .flatMap(typeId -> deletePoliciesForType(typeId, excludePolicyIds), CONCURRENCY_RIC);
    }

    @Override
    public Mono<String> getPolicyStatus(Policy policy) {
        String statusUri = uri.createGetPolicyStatusUri(policy.getType().getId(), policy.getId());
        return restClient.get(statusUri);

    }

    private Flux<String> getPolicyTypeIds() {
        return restClient.get(uri.createPolicyTypesUri()) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Flux<String> getPolicyIdentitiesByType(String typeId) {
        return restClient.get(uri.createGetPolicyIdsUri(typeId)) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Mono<String> deletePolicyById(String typeId, String policyId) {
        String policyUri = uri.createDeleteUri(typeId, policyId);
        return restClient.delete(policyUri);
    }

    private Flux<String> deletePoliciesForType(String typeId, Set<String> excludePolicyIds) {
        return getPolicyIdentitiesByType(typeId) //
                .filter(policyId -> !excludePolicyIds.contains(policyId)) //
                .flatMap(policyId -> deletePolicyById(typeId, policyId), CONCURRENCY_RIC);
    }
}
