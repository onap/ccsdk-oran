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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.RappSimulatorController;
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
import reactor.util.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Component
public class TestHelper {

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

    public int port;

    public AsyncRestClient restClientV3() {
        return restClientV3(false);
    }

    public AsyncRestClient restClient() {
        return restClient(false);
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

    public void putService(String name) throws JsonProcessingException {
        putService(name, 0, null);
    }

    public void putService(String name, long keepAliveIntervalSeconds, @Nullable HttpStatus expectedStatus) throws JsonProcessingException {
        String url = "/services";
        String body = createServiceJson(name, keepAliveIntervalSeconds);
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        if (expectedStatus != null) {
            assertNotNull(resp);
            assertEquals(expectedStatus, resp.getStatusCode(), "");
        }
    }

    public PolicyType createPolicyType(String policyTypeName) {
        return PolicyType.builder()
                .id(policyTypeName)
                .schema("{\"title\":\"" + policyTypeName + "\"}")
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
    public PolicyType addPolicyType(String policyTypeName, String ricId) {
        PolicyType type = createPolicyType(policyTypeName);
        policyTypes.put(type);
        addRic(ricId).addSupportedPolicyType(type);
        return type;
    }

    public Map<String,String> jsonString() {
        Map<String,String> policyDataInMap = new HashMap<>();
        policyDataInMap.put("servingCellNrcgi","1");
        return policyDataInMap;
    }

    public String postPolicyBody(String nearRtRicId, String policyTypeName) throws JsonProcessingException {
        PolicyObjectInformation policyObjectInfo = new PolicyObjectInformation(nearRtRicId, jsonString());
        policyObjectInfo.setPolicyTypeId(policyTypeName);
        policyObjectInfo.setPolicyObject(dummyPolicyObject());
        return objectMapper.writeValueAsString(policyObjectInfo);
    }

    public PolicyObjectInformation policyObjectInfo(String nearRtRicId, String policyTypeName) {
        PolicyObjectInformation policyObjectInfo = new PolicyObjectInformation(nearRtRicId, jsonString());
        policyObjectInfo.setPolicyTypeId(policyTypeName);
        policyObjectInfo.setPolicyObject(dummyPolicyObject());
        return policyObjectInfo;
    }

    private Map<String,String> dummyPolicyObject() {
        Map<String,String> policyDataInMap = new HashMap<>();
        policyDataInMap.put("servingCellNrcgi","1");
        return policyDataInMap;
    }

    public String createServiceJson(String name, long keepAliveIntervalSeconds) throws JsonProcessingException {
        String callbackUrl = baseUrl() + RappSimulatorController.SERVICE_CALLBACK_URL;
        return createServiceJson(name, keepAliveIntervalSeconds, callbackUrl);
    }

    public String createServiceJson(String name, long keepAliveIntervalSeconds, String url) throws JsonProcessingException {
        org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceRegistrationInfo service = new org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceRegistrationInfo(name)
                .keepAliveIntervalSeconds(keepAliveIntervalSeconds)
                .callbackUrl(url);

        return objectMapper.writeValueAsString(service);
    }

    public void testSuccessResponse(Mono<ResponseEntity<String>> responseEntityMono, HttpStatus httpStatusCode,
                                    String responseContains) {
        StepVerifier.create(responseEntityMono)
                .expectNextMatches(responseEntity -> {
                    // Assert status code
                    HttpStatusCode status = responseEntity.getStatusCode();
                    String res = responseEntity.getBody();
                    assertThat(res).contains(responseContains);
                    return status.value() == httpStatusCode.value();
                })
                .expectComplete()
                .verify();
    }

    public void testErrorCode(Mono<?> request, HttpStatus expStatus) {
        testErrorCode(request, expStatus, "", true);
    }

    public void testErrorCode(Mono<?> request, HttpStatus expStatus, boolean expectApplicationProblemJsonMediaType) {
        testErrorCode(request, expStatus, "", expectApplicationProblemJsonMediaType);
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
}
