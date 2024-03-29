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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StdA1ClientTest {
    private static final String RIC_URL = "RicUrl";
    private static final String POLICY_TYPE_1_NAME = "type1";
    private static final String POLICY_1_ID = "policy1";
    private static final String POLICY_2_ID = "policy2";
    private static final String POLICY_JSON = "{\"policyId\":\"policy1\"}";
    private static final String POLICY_TYPE = "typeName";

    StdA1ClientVersion1 clientUnderTest;

    @Mock
    AsyncRestClient asyncRestClientMock;

    @Mock
    RicConfig ricConfigMock;

    @BeforeEach
    void init() {
        clientUnderTest = new StdA1ClientVersion1(asyncRestClientMock, ricConfigMock);
    }

    private String policiesUrl() {
        return RIC_URL + "/A1-P/v1/policies";
    }

    private String policiesBaseUrl() {
        return policiesUrl() + "/";
    }

    @Test
    @DisplayName("test Get Policy Type Identities")
    void testGetPolicyTypeIdentities() {
        List<String> policyTypeIds = clientUnderTest.getPolicyTypeIdentities().block();
        assertEquals(1, policyTypeIds.size(), "should hardcoded to one");
        assertEquals("", policyTypeIds.get(0), "should hardcoded to empty");
    }

    @Test
    @DisplayName("test Get Policy Identities")
    void testGetPolicyIdentities() {
        doReturn(RIC_URL).when(ricConfigMock).getBaseUrl();
        Mono<String> policyIds = Mono.just(Arrays.asList(POLICY_1_ID, POLICY_2_ID).toString());
        when(asyncRestClientMock.get(anyString())).thenReturn(policyIds);

        List<String> result = clientUnderTest.getPolicyIdentities().block();
        assertEquals(2, result.size(), "");

        verify(asyncRestClientMock).get(policiesUrl());
    }

    @Test
    @DisplayName("test Get Valid Policy Type")
    void testGetValidPolicyType() {
        String policyType = clientUnderTest.getPolicyTypeSchema(POLICY_TYPE_1_NAME).block();
        assertEquals("{}", policyType, "");
    }

    @Test
    @DisplayName("test Put Policy Valid Response")
    void testPutPolicyValidResponse() {
        doReturn(RIC_URL).when(ricConfigMock).getBaseUrl();
        when(asyncRestClientMock.put(anyString(), anyString())).thenReturn(Mono.just(POLICY_JSON));

        Mono<String> policyMono =
                clientUnderTest.putPolicy(A1ClientHelper.createPolicy(RIC_URL, POLICY_1_ID, POLICY_JSON, POLICY_TYPE));

        verify(asyncRestClientMock).put(policiesBaseUrl() + POLICY_1_ID, POLICY_JSON);
        StepVerifier.create(policyMono).expectNext(POLICY_JSON).expectComplete().verify();
    }

    @Test
    @DisplayName("test Delete Policy")
    void testDeletePolicy() {
        doReturn(RIC_URL).when(ricConfigMock).getBaseUrl();
        final String url = policiesBaseUrl() + POLICY_1_ID;
        when(asyncRestClientMock.delete(url)).thenReturn(Mono.empty());

        Policy policy = A1ClientHelper.createPolicy(RIC_URL, POLICY_1_ID, POLICY_JSON, POLICY_TYPE);
        Mono<?> responseMono = clientUnderTest.deletePolicy(policy);
        verify(asyncRestClientMock).delete(url);
        StepVerifier.create(responseMono).expectComplete().verify();
    }

    @Test
    @DisplayName("test Delete All Policies")
    void testDeleteAllPolicies() {
        doReturn(RIC_URL).when(ricConfigMock).getBaseUrl();
        Mono<String> policyIds = Mono.just(Arrays.asList(POLICY_1_ID, POLICY_2_ID).toString());
        when(asyncRestClientMock.get(policiesUrl())).thenReturn(policyIds);
        when(asyncRestClientMock.delete(anyString())).thenReturn(Mono.empty());

        Flux<String> responseFlux = clientUnderTest.deleteAllPolicies();
        StepVerifier.create(responseFlux).expectComplete().verify();
        verify(asyncRestClientMock).get(policiesUrl());
        verify(asyncRestClientMock).delete(policiesBaseUrl() + POLICY_1_ID);
        verify(asyncRestClientMock).delete(policiesBaseUrl() + POLICY_2_ID);
    }
}
