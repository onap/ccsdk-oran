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

package org.onap.ccsdk.oran.a1policymanagementservice.utils.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Component
public class TestHelperTest {

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    ApplicationContext context;

    @Autowired
    private Rics rics;

    @Autowired
    private Policies policies;

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Gson gson;

    public int port;

    public AsyncRestClient restClientV3() {
        return restClientV3(false);
    }

    public AsyncRestClient restClient(String baseUrl, boolean useTrustValidation) {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        config = WebClientConfig.builder()
                .keyStoreType(config.getKeyStoreType())
                .keyStorePassword(config.getKeyStorePassword())
                .keyStore(config.getKeyStore())
                .keyPassword(config.getKeyPassword())
                .isTrustStoreUsed(useTrustValidation)
                .trustStore(config.getTrustStore())
                .trustStorePassword(config.getTrustStorePassword())
                .httpProxyConfig(config.getHttpProxyConfig())
                .build();

        AsyncRestClientFactory f = new AsyncRestClientFactory(config, new SecurityContext(""));
        return f.createRestClientNoHttpProxy(baseUrl);

    }

    public String baseUrl() {
        return "https://localhost:" + port;
    }

    public AsyncRestClient restClientV3(boolean useTrustValidation) {
        return restClient(baseUrl() + Consts.V3_API_ROOT, useTrustValidation);
    }

    public AsyncRestClient restClient(boolean useTrustValidation) {
        return restClient(baseUrl() + Consts.V2_API_ROOT, useTrustValidation);
    }

    public PolicyType createPolicyType(String policyTypeName, String filePath) throws IOException {
        InputStream in = getClass().getResourceAsStream(filePath);
        assert in != null;
        String schema = CharStreams.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
        return PolicyType.builder()
                .id(policyTypeName)
                .schema(schema)
                .build();
    }

    public Ric addRic(String ricId) {
        return addRic(ricId, null);
    }

    public RicConfig ricConfig(String ricId, String managedElement) {
        List<String> mes = new ArrayList<>();
        if (managedElement != null) {
            mes.add(managedElement);
        }
        return RicConfig.builder()
                .ricId(ricId)
                .baseUrl(ricId)
                .managedElementIds(mes)
                .build();
    }
    public Ric addRic(String ricId, String managedElement) {
        if (rics.get(ricId) != null) {
            return rics.get(ricId);
        }

        RicConfig conf = ricConfig(ricId, managedElement);
        Ric ric = new Ric(conf);
        ric.setState(Ric.RicState.AVAILABLE);
        this.rics.put(ric);
        return ric;
    }
    public PolicyType addPolicyType(String policyTypeName, String ricId) throws IOException {
        PolicyType type = createPolicyType(policyTypeName, "/policy_types/demo-policy-schema-3.json");
        policyTypes.put(type);
        addRic(ricId).addSupportedPolicyType(type);
        return type;
    }

    public String postPolicyBody(String nearRtRicId, String policyTypeName, String policyId) {
        PolicyObjectInformation policyObjectInfo = new PolicyObjectInformation(nearRtRicId, dummyPolicyObject(), policyTypeName);
        if (policyId != null && !policyId.isEmpty() && !policyId.isBlank())
            policyObjectInfo.setPolicyId(policyId);
        return gson.toJson(policyObjectInfo);
    }

    public String putPolicyBody(String nearRtRicId, String policyTypeName, String policyId, String ueId, String qosId,
                                String priorityLevel) {
        PolicyObjectInformation policyObjectInfo = new PolicyObjectInformation(nearRtRicId, dummyPolicyObjectForPut(
                ueId, qosId, priorityLevel), policyTypeName);
        if (policyId != null && !policyId.isEmpty() && !policyId.isBlank())
            policyObjectInfo.setPolicyId(policyId);
        return gson.toJson(policyObjectInfo);
    }

    public PolicyObjectInformation policyObjectInfo(String nearRtRicId, String policyTypeName) {
        return gson.fromJson(postPolicyBody(nearRtRicId, policyTypeName, ""), PolicyObjectInformation.class);
    }

    public JsonObject dummyPolicyObjectForPut(String... values) {
        return JsonParser.parseString("{\n" +
                "        \"scope\": {\n" +
                "            \"ueId\": \"" + values[0] + "\",\n" +
                "            \"qosId\": \"" + values[1] + "\"\n" +
                "        },\n" +
                "        \"qosObjectives\": {\n" +
                "            \"priorityLevel\": " + values[2] + "\n" +
                "        }\n" +
                "    }").getAsJsonObject();
    }

    public JsonObject dummyPolicyObject() {
        return JsonParser.parseString("{\n" +
                "        \"scope\": {\n" +
                "            \"ueId\": \"ue5100\",\n" +
                "            \"qosId\": \"qos5100\"\n" +
                "        },\n" +
                "        \"qosObjectives\": {\n" +
                "            \"priorityLevel\": 5100.0\n" +
                "        }\n" +
                "    }").getAsJsonObject();
    }

    public Policy buidTestPolicy(PolicyObjectInformation policyInfo, String id) throws Exception{
        return Policy.builder().ric(rics.getRic(policyInfo.getNearRtRicId()))
                .type(policyTypes.getType(policyInfo.getPolicyTypeId()))
                .json(objectMapper.writeValueAsString(policyInfo.getPolicyObject()))
                .lastModified(Instant.now())
                .id(id).build();
    }

    public <T> void testSuccessResponse(Mono<ResponseEntity<T>> responseEntityMono, HttpStatus httpStatusCode,
                                               Predicate<T> responsePredicate) {
        StepVerifier.create(responseEntityMono)
                .expectNextMatches(responseEntity -> {
                    // Assert status code
                    HttpStatusCode status = responseEntity.getStatusCode();
                    T responseBody = responseEntity.getBody();
                    assert responsePredicate.test(responseBody);
                    return status.value() == httpStatusCode.value();
                })
                .expectComplete()
                .verify();
    }

    public void testSuccessHeader(Mono<ResponseEntity<String>> responseEntityMono, String headerKey,
                                  Predicate<String> responsePredicate) {
        StepVerifier.create(responseEntityMono)
                .expectNextMatches(responseEntity -> {
                    String headerValue = Objects.requireNonNull(responseEntity.getHeaders().get(headerKey)).get(0);
                    return responsePredicate.test(headerValue);
                })
                .expectComplete()
                .verify();
    }

    public void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        testErrorCode(request, expStatus, responseContains, true);
    }

    public void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains,
                               boolean expectApplicationProblemJsonMediaType) {
        StepVerifier.create(request)
                .expectSubscription()
                .expectErrorMatches(
                        t -> checkWebClientError(t, expStatus, responseContains, expectApplicationProblemJsonMediaType))
                .verify();
    }

    private boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains,
                                        boolean expectApplicationProblemJsonMediaType) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        String body = responseException.getResponseBodyAsString();
        assertThat(body).contains(responseContains);
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);

        if (expectApplicationProblemJsonMediaType) {
            assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return true;
    }

    public void verifyMockError(Mono<?> responseMono, String responseCheck) {
        StepVerifier.create(responseMono)
                .expectSubscription()
                .expectErrorMatches(response -> {
                    String status = response.getMessage();
                    return status.contains(responseCheck);
                })
                .verify();
    }

    public String configAsString() throws Exception {
        File configFile =
                new File(Objects.requireNonNull(getClass().getClassLoader().getResource("test_application_configuration.json")).getFile());
        return FileUtils.readFileToString(configFile, "UTF-8");
    }
}
