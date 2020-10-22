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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StdA1ClientV2Test {

    private static final String RIC_URL = "https://ric.com";

    private static final String RIC_BASE_URL = RIC_URL + "/A1-P/v2";

    private static final String POLICYTYPES_IDENTITIES_URL = RIC_BASE_URL + "/policytypes";
    private static final String POLICIES = "/policies";
    private static final String POLICYTYPES_URL = RIC_BASE_URL + "/policytypes/";
    private static final String POLICY_TYPE_1_ID = "type1";
    private static final String POLICY_TYPE_2_ID = "type2";
    private static final String POLICY_TYPE_SCHEMA_VALID = "{\"type\":\"type1\"}";
    private static final String POLICY_TYPE_SCHEMA_INVALID = "\"type\":\"type1\"}";
    private static final String POLICY_1_ID = "policy1";
    private static final String POLICY_2_ID = "policy2";
    private static final String POLICY_JSON_VALID = "{\"policyId\":\"policy1\"}";

    StdA1ClientVersion2 clientUnderTest;

    AsyncRestClient asyncRestClientMock;

    @BeforeEach
    void init() {
        RicConfig ricConfig = ImmutableRicConfig.builder() //
                .ricId("name") //
                .baseUrl(RIC_URL) //
                .managedElementIds(new ArrayList<>()) //
                .controllerName("") //
                .build();
        asyncRestClientMock = mock(AsyncRestClient.class);
        clientUnderTest = new StdA1ClientVersion2(ricConfig, asyncRestClientMock);
    }

    @Test
    void testGetPolicyTypeIdentities() {
        List<String> policyTypeIds = Arrays.asList(POLICY_TYPE_1_ID, POLICY_TYPE_2_ID);
        Mono<String> policyTypeIdsResp = Mono.just(policyTypeIds.toString());
        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeIdsResp);

        Mono<List<String>> returnedMono = clientUnderTest.getPolicyTypeIdentities();
        verify(asyncRestClientMock).get(POLICYTYPES_IDENTITIES_URL);
        StepVerifier.create(returnedMono).expectNext(policyTypeIds).expectComplete().verify();
    }

    @Test
    void testGetPolicyIdentities() {
        Mono<String> policyTypeIdsResp = Mono.just(Arrays.asList(POLICY_TYPE_1_ID, POLICY_TYPE_2_ID).toString());
        Mono<String> policyIdsType1Resp = Mono.just(Arrays.asList(POLICY_1_ID).toString());
        Mono<String> policyIdsType2Resp = Mono.just(Arrays.asList(POLICY_2_ID).toString());
        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeIdsResp).thenReturn(policyIdsType1Resp)
                .thenReturn(policyIdsType2Resp);

        List<String> returned = clientUnderTest.getPolicyIdentities().block();

        assertEquals(2, returned.size(), "");
        verify(asyncRestClientMock).get(POLICYTYPES_IDENTITIES_URL);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_1_ID + POLICIES);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_2_ID + POLICIES);
    }

    @Test
    void testGetValidPolicyType() {
        String policyType = "{\"policySchema\": " + POLICY_TYPE_SCHEMA_VALID + "}";
        Mono<String> policyTypeResp = Mono.just(policyType);

        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeResp);

        String response = clientUnderTest.getPolicyTypeSchema(POLICY_TYPE_1_ID).block();
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_1_ID);
        assertThat(response).contains("title");
    }

    @Test
    void testGetInValidPolicyTypeJson() {
        String policyType = "{\"policySchema\": " + POLICY_TYPE_SCHEMA_INVALID + "}";
        Mono<String> policyTypeResp = Mono.just(policyType);

        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeResp);

        Mono<String> returnedMono = clientUnderTest.getPolicyTypeSchema(POLICY_TYPE_1_ID);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_1_ID);
        StepVerifier.create(returnedMono).expectErrorMatches(throwable -> throwable instanceof JSONException).verify();
    }

    @Test
    void testGetPolicyTypeWithoutCreateSchema() {
        Mono<String> policyTypeResp = Mono.just(POLICY_TYPE_SCHEMA_VALID);

        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeResp);

        Mono<String> returnedMono = clientUnderTest.getPolicyTypeSchema(POLICY_TYPE_1_ID);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_1_ID);
        StepVerifier.create(returnedMono).expectErrorMatches(throwable -> throwable instanceof Exception).verify();
    }

    @Test
    void testPutPolicy() {
        when(asyncRestClientMock.put(anyString(), anyString())).thenReturn(Mono.empty());

        clientUnderTest
                .putPolicy(A1ClientHelper.createPolicy(RIC_URL, POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID))
                .block();
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRestClientMock).put(urlCaptor.capture(), eq(POLICY_JSON_VALID));
        String actualUrl = urlCaptor.getValue();
        String expUrl = POLICYTYPES_URL + POLICY_TYPE_1_ID + POLICIES + "/" + POLICY_1_ID;
        assertThat(actualUrl).contains(expUrl);
    }

    @Test
    void testDeletePolicy() {
        when(asyncRestClientMock.delete(anyString())).thenReturn(Mono.empty());

        Mono<String> returnedMono = clientUnderTest
                .deletePolicy(A1ClientHelper.createPolicy(RIC_URL, POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID));
        verify(asyncRestClientMock).delete(POLICYTYPES_URL + POLICY_TYPE_1_ID + POLICIES + "/" + POLICY_1_ID);
        StepVerifier.create(returnedMono).expectComplete().verify();
    }

    @Test
    void testDeleteAllPolicies() {
        Mono<String> policyTypeIdsResp = Mono.just(Arrays.asList(POLICY_TYPE_1_ID, POLICY_TYPE_2_ID).toString());
        Mono<String> policyIdsType1Resp = Mono.just(Arrays.asList(POLICY_1_ID).toString());
        Mono<String> policyIdsType2Resp = Mono.just(Arrays.asList(POLICY_2_ID).toString());
        when(asyncRestClientMock.get(anyString())).thenReturn(policyTypeIdsResp).thenReturn(policyIdsType1Resp)
                .thenReturn(policyIdsType2Resp);
        when(asyncRestClientMock.delete(anyString())).thenReturn(Mono.empty());

        Flux<String> returnedFlux = clientUnderTest.deleteAllPolicies();
        StepVerifier.create(returnedFlux).expectComplete().verify();
        verify(asyncRestClientMock).get(POLICYTYPES_IDENTITIES_URL);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_1_ID + POLICIES);
        verify(asyncRestClientMock).delete(POLICYTYPES_URL + POLICY_TYPE_1_ID + POLICIES + "/" + POLICY_1_ID);
        verify(asyncRestClientMock).get(POLICYTYPES_URL + POLICY_TYPE_2_ID + POLICIES);
        verify(asyncRestClientMock).delete(POLICYTYPES_URL + POLICY_TYPE_2_ID + POLICIES + "/" + POLICY_2_ID);
    }
}
