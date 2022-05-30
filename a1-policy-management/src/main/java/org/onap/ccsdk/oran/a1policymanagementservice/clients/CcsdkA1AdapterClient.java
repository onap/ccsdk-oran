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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.Getter;

import org.json.JSONObject;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for accessing the A1 adapter in the CCSDK in ONAP.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class CcsdkA1AdapterClient implements A1Client {

    static final int CONCURRENCY_RIC = 1; // How many paralell requests that is sent to one NearRT RIC

    @Getter
    public static class AdapterRequest {
        private String nearRtRicUrl = null;
        private String body = null;

        public AdapterRequest(String url, String body) {
            this.nearRtRicUrl = url;
            this.body = body;
        }

        public AdapterRequest() {}
    }

    @Getter
    public static class AdapterOutput {
        private String body = null;
        private int httpStatus = 0;

        public AdapterOutput(int status, String body) {
            this.httpStatus = status;
            this.body = body;
        }

        public AdapterOutput() {}
    }

    static com.google.gson.Gson gson = new GsonBuilder() //
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES) //
            .create(); //

    private static final String GET_POLICY_RPC = "getA1Policy";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final ControllerConfig controllerConfig;
    private final AsyncRestClient restClient;
    private final RicConfig ricConfig;
    private final A1ProtocolType protocolType;

    /**
     * Constructor that creates the REST client to use.
     *
     * @param protocolType the southbound protocol of the controller. Supported
     *        protocols are CCSDK_A1_ADAPTER_STD_V1_1,
     *        CCSDK_A1_ADAPTER_OSC_V1 and
     *        CCSDK_A1_ADAPTER_STD_V2_0_0 with
     * @param ricConfig the configuration of the Near-RT RIC to communicate
     *        with
     * @param controllerConfig the configuration of the CCSDK A1 Adapter to use
     *
     * @throws IllegalArgumentException when the protocolType is wrong.
     */
    public CcsdkA1AdapterClient(A1ProtocolType protocolType, RicConfig ricConfig, ControllerConfig controllerConfig,
            AsyncRestClientFactory restClientFactory) {
        this(protocolType, ricConfig, controllerConfig,
                restClientFactory.createRestClientNoHttpProxy(controllerConfig.getBaseUrl() + "/restconf/operations"));
    }

    /**
     * Constructor where the REST client to use is provided.
     *
     * @param protocolType the southbound protocol of the controller. Supported
     *        protocols are CCSDK_A1_ADAPTER_STD_V1_1,
     *        CCSDK_A1_ADAPTER_OSC_V1 and
     *        CCSDK_A1_ADAPTER_STD_V2_0_0 with
     * @param ricConfig the configuration of the Near-RT RIC to communicate
     *        with
     * @param controllerConfig the configuration of the CCSDK A1 Adapter to use
     * @param restClient the REST client to use
     *
     * @throws IllegalArgumentException when the protocolType is illegal.
     */
    CcsdkA1AdapterClient(A1ProtocolType protocolType, RicConfig ricConfig, ControllerConfig controllerConfig,
            AsyncRestClient restClient) {
        if (A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1.equals(protocolType) //
                || A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1.equals(protocolType) //
                || A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0.equals(protocolType)) {
            this.restClient = restClient;
            this.ricConfig = ricConfig;
            this.protocolType = protocolType;
            this.controllerConfig = controllerConfig;
            logger.debug("CcsdkA1AdapterClient for ric: {}, a1Controller: {}", ricConfig.getRicId(), controllerConfig);
        } else {
            logger.error("Not supported protocoltype: {}", protocolType);
            throw new IllegalArgumentException("Not handeled protocolversion: " + protocolType);
        }
    }

    @Override
    public Mono<List<String>> getPolicyTypeIdentities() {
        if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) {
            return Mono.just(Arrays.asList(""));
        } else {
            return post(GET_POLICY_RPC, getUriBuilder().createPolicyTypesUri(), Optional.empty()) //
                    .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString) //
                    .collectList();
        }
    }

    @Override
    public Mono<List<String>> getPolicyIdentities() {
        return getPolicyIds() //
                .collectList();
    }

    @Override
    public Mono<String> getPolicyTypeSchema(String policyTypeId) {
        if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) {
            return Mono.just("{}");
        } else {
            A1UriBuilder uri = this.getUriBuilder();
            final String ricUrl = uri.createGetSchemaUri(policyTypeId);
            return post(GET_POLICY_RPC, ricUrl, Optional.empty()) //
                    .flatMap(response -> extractCreateSchema(response, policyTypeId));
        }
    }

    private Mono<String> extractCreateSchema(String controllerResponse, String policyTypeId) {
        if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1) {
            return OscA1Client.extractCreateSchema(controllerResponse, policyTypeId);
        } else if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0) {
            return StdA1ClientVersion2.extractPolicySchema(controllerResponse, policyTypeId);
        } else {
            return Mono.error(new ServiceException("Not supported " + this.protocolType));
        }
    }

    @Override
    public Mono<String> putPolicy(Policy policy) {
        String ricUrl = getUriBuilder().createPutPolicyUri(policy.getType().getId(), policy.getId(),
                policy.getStatusNotificationUri());
        return post("putA1Policy", ricUrl, Optional.of(policy.getJson()));
    }

    @Override
    public Mono<String> deletePolicy(Policy policy) {
        return deletePolicyById(policy.getType().getId(), policy.getId());
    }

    @Override
    public Flux<String> deleteAllPolicies() {
        if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) {
            return getPolicyIds() //
                    .flatMap(policyId -> deletePolicyById("", policyId), CONCURRENCY_RIC); //
        } else {
            A1UriBuilder uriBuilder = this.getUriBuilder();
            return getPolicyTypeIdentities() //
                    .flatMapMany(Flux::fromIterable) //
                    .flatMap(type -> deleteAllInstancesForType(uriBuilder, type), CONCURRENCY_RIC);
        }
    }

    private Flux<String> getInstancesForType(A1UriBuilder uriBuilder, String type) {
        return post(GET_POLICY_RPC, uriBuilder.createGetPolicyIdsUri(type), Optional.empty()) //
                .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
    }

    private Flux<String> deleteAllInstancesForType(A1UriBuilder uriBuilder, String type) {
        return getInstancesForType(uriBuilder, type) //
                .flatMap(instance -> deletePolicyById(type, instance), CONCURRENCY_RIC);
    }

    @Override
    public Mono<A1ProtocolType> getProtocolVersion() {
        return tryStdProtocolVersion2() //
                .onErrorResume(t -> tryStdProtocolVersion1()) //
                .onErrorResume(t -> tryOscProtocolVersion());
    }

    @Override
    public Mono<String> getPolicyStatus(Policy policy) {
        String ricUrl = getUriBuilder().createGetPolicyStatusUri(policy.getType().getId(), policy.getId());
        return post("getA1PolicyStatus", ricUrl, Optional.empty());

    }

    private A1UriBuilder getUriBuilder() {
        if (protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) {
            return new StdA1ClientVersion1.UriBuilder(ricConfig);
        } else if (protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0) {
            return new StdA1ClientVersion2.OranV2UriBuilder(ricConfig);
        } else if (protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1) {
            return new OscA1Client.UriBuilder(ricConfig);
        }
        logger.error("Not supported protocoltype: {}", protocolType);
        throw new NullPointerException();
    }

    private Mono<A1ProtocolType> tryOscProtocolVersion() {
        OscA1Client.UriBuilder oscApiuriBuilder = new OscA1Client.UriBuilder(ricConfig);
        return post(GET_POLICY_RPC, oscApiuriBuilder.createHealtcheckUri(), Optional.empty()) //
                .flatMap(x -> Mono.just(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1));
    }

    private Mono<A1ProtocolType> tryStdProtocolVersion1() {
        StdA1ClientVersion1.UriBuilder uriBuilder = new StdA1ClientVersion1.UriBuilder(ricConfig);
        return post(GET_POLICY_RPC, uriBuilder.createGetPolicyIdsUri(""), Optional.empty()) //
                .flatMap(x -> Mono.just(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1));
    }

    private Mono<A1ProtocolType> tryStdProtocolVersion2() {
        StdA1ClientVersion2.OranV2UriBuilder uriBuilder = new StdA1ClientVersion2.OranV2UriBuilder(ricConfig);
        return post(GET_POLICY_RPC, uriBuilder.createPolicyTypesUri(), Optional.empty()) //
                .flatMap(x -> Mono.just(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0));
    }

    private Flux<String> getPolicyIds() {
        if (this.protocolType == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) {
            StdA1ClientVersion1.UriBuilder uri = new StdA1ClientVersion1.UriBuilder(ricConfig);
            final String ricUrl = uri.createGetPolicyIdsUri("");
            return post(GET_POLICY_RPC, ricUrl, Optional.empty()) //
                    .flatMapMany(A1AdapterJsonHelper::parseJsonArrayOfString);
        } else {
            A1UriBuilder uri = this.getUriBuilder();
            return getPolicyTypeIdentities() //
                    .flatMapMany(Flux::fromIterable)
                    .flatMap(type -> post(GET_POLICY_RPC, uri.createGetPolicyIdsUri(type), Optional.empty())) //
                    .flatMap(A1AdapterJsonHelper::parseJsonArrayOfString);
        }
    }

    private Mono<String> deletePolicyById(String type, String policyId) {
        String ricUrl = getUriBuilder().createDeleteUri(type, policyId);
        return post("deleteA1Policy", ricUrl, Optional.empty());
    }

    private Mono<String> post(String rpcName, String ricUrl, Optional<String> body) {
        AdapterRequest inputParams = new AdapterRequest(ricUrl, body.isPresent() ? body.get() : null);

        final String inputJsonString = A1AdapterJsonHelper.createInputJsonString(inputParams);
        logger.debug("POST inputJsonString = {}", inputJsonString);

        return restClient
                .postWithAuthHeader(controllerUrl(rpcName), inputJsonString, this.controllerConfig.getUserName(),
                        this.controllerConfig.getPassword()) //
                .flatMap(this::extractResponseBody);
    }

    private Mono<String> extractResponse(JSONObject responseOutput) {
        AdapterOutput output = gson.fromJson(responseOutput.toString(), AdapterOutput.class);

        String body = output.body == null ? "" : output.body;
        if (HttpStatus.valueOf(output.httpStatus).is2xxSuccessful()) {
            return Mono.just(body);
        } else {
            logger.debug("Error response: {} {}", output.httpStatus, body);
            byte[] responseBodyBytes = body.getBytes(StandardCharsets.UTF_8);
            HttpStatus httpStatus = HttpStatus.valueOf(output.httpStatus);
            WebClientResponseException responseException = new WebClientResponseException(httpStatus.value(),
                    httpStatus.getReasonPhrase(), null, responseBodyBytes, StandardCharsets.UTF_8, null);

            return Mono.error(responseException);
        }
    }

    private Mono<String> extractResponseBody(String responseStr) {
        return A1AdapterJsonHelper.getOutput(responseStr) //
                .flatMap(this::extractResponse);
    }

    private String controllerUrl(String rpcName) {
        return "/A1-ADAPTER-API:" + rpcName;
    }
}
