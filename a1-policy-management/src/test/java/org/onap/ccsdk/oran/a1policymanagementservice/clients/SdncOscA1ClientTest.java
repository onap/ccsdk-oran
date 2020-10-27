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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.ImmutableAdapterOutput.Builder;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.CcsdkA1AdapterClient.AdapterOutput;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.CcsdkA1AdapterClient.AdapterRequest;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SdncOscA1ClientTest {
    private static final String CONTROLLER_USERNAME = "username";
    private static final String CONTROLLER_PASSWORD = "password";
    private static final String RIC_1_URL = "RicUrl";
    private static final String GET_A1_POLICY_URL = "/A1-ADAPTER-API:getA1Policy";
    private static final String PUT_A1_URL = "/A1-ADAPTER-API:putA1Policy";
    private static final String DELETE_A1_URL = "/A1-ADAPTER-API:deleteA1Policy";
    private static final String GET_A1_POLICY_STATUS_URL = "/A1-ADAPTER-API:getA1PolicyStatus";
    private static final String POLICY_TYPE_1_ID = "type1";
    private static final String POLICY_1_ID = "policy1";
    private static final String POLICY_JSON_VALID = "{\"scope\":{\"ueId\":\"ue1\"}}";

    CcsdkA1AdapterClient clientUnderTest;

    @Mock
    AsyncRestClient asyncRestClientMock;

    private ControllerConfig controllerConfig() {
        return ImmutableControllerConfig.builder() //
                .name("name") //
                .baseUrl("baseUrl") //
                .password(CONTROLLER_PASSWORD) //
                .userName(CONTROLLER_USERNAME) //
                .build();
    }

    @Test
    void createClientWithWrongProtocol_thenErrorIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CcsdkA1AdapterClient(A1ProtocolType.STD_V1_1, null, null, new AsyncRestClient("", null));
        });
    }

    @Test
    void getPolicyTypeIdentities_STD_V1() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);
        List<String> policyTypeIds = clientUnderTest.getPolicyTypeIdentities().block();
        assertEquals(1, policyTypeIds.size(), "should hardcoded to one");
        assertEquals("", policyTypeIds.get(0), "should hardcoded to empty");
    }

    private void testGetPolicyTypeIdentities(A1ProtocolType protocolType, String expUrl) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        String response = createOkResponseWithBody(Arrays.asList(POLICY_TYPE_1_ID));
        whenAsyncPostThenReturn(Mono.just(response));

        List<String> policyTypeIds = clientUnderTest.getPolicyTypeIdentities().block();

        assertEquals(1, policyTypeIds.size());
        assertEquals(POLICY_TYPE_1_ID, policyTypeIds.get(0));

        ImmutableAdapterRequest expectedParams = ImmutableAdapterRequest.builder() //
                .nearRtRicUrl(expUrl) //
                .build();
        String expInput = SdncJsonHelper.createInputJsonString(expectedParams);
        verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    void getPolicyTypeIdentities_OSC() {
        testGetPolicyTypeIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, RIC_1_URL + "/a1-p/policytypes");
    }

    @Test
    void getPolicyTypeIdentities_STD_V2() {
        testGetPolicyTypeIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, RIC_1_URL + "/A1-P/v2/policytypes");
    }

    @Test
    void getTypeSchema_STD_V1() {

        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        String policyType = clientUnderTest.getPolicyTypeSchema("").block();

        assertEquals("{}", policyType);
    }

    private void testGetTypeSchema(A1ProtocolType protocolType, String expUrl, String policyTypeId,
            String getSchemaResponseFile) throws IOException {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        String ricResponse = loadFile(getSchemaResponseFile);
        JsonElement elem = gson().fromJson(ricResponse, JsonElement.class);
        String responseFromController = createOkResponseWithBody(elem);
        whenAsyncPostThenReturn(Mono.just(responseFromController));

        String response = clientUnderTest.getPolicyTypeSchema(policyTypeId).block();

        JsonElement respJson = gson().fromJson(response, JsonElement.class);
        assertEquals(policyTypeId, respJson.getAsJsonObject().get("title").getAsString(),
                "title should be updated to contain policyType ID");

        ImmutableAdapterRequest expectedParams = ImmutableAdapterRequest.builder() //
                .nearRtRicUrl(expUrl) //
                .build();
        String expInput = SdncJsonHelper.createInputJsonString(expectedParams);

        verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    void getTypeSchema_OSC() throws IOException {
        String expUrl = RIC_1_URL + "/a1-p/policytypes/policyTypeId";
        testGetTypeSchema(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl, "policyTypeId", "test_osc_get_schema_response.json");
    }

    @Test
    void getTypeSchema_STD_V2() throws IOException {
        String expUrl = RIC_1_URL + "/A1-P/v2/policytypes/policyTypeId";
        testGetTypeSchema(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl, "policyTypeId",
                "test_oran_get_schema_response.json");
    }

    @Test
    void parseJsonArrayOfString() {
        // One integer and one string
        String inputString = "[1, \"1\" ]";

        List<String> result = SdncJsonHelper.parseJsonArrayOfString(inputString).collectList().block();
        assertEquals(2, result.size());
        assertEquals("1", result.get(0));
        assertEquals("1", result.get(1));
    }

    private void getPolicyIdentities(A1ProtocolType protocolType, String... expUrls) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);
        String resp = createOkResponseWithBody(Arrays.asList("xxx"));
        whenAsyncPostThenReturn(Mono.just(resp));

        List<String> returned = clientUnderTest.getPolicyIdentities().block();

        assertEquals(1, returned.size());
        for (String expUrl : expUrls) {
            ImmutableAdapterRequest expectedParams = ImmutableAdapterRequest.builder() //
                    .nearRtRicUrl(expUrl) //
                    .build();
            String expInput = SdncJsonHelper.createInputJsonString(expectedParams);
            verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                    CONTROLLER_PASSWORD);
        }
    }

    @Test
    void getPolicyIdentities_STD_V1() {
        String expUrl = RIC_1_URL + "/A1-P/v1/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl);
    }

    @Test
    void getPolicyIdentities_STD_V2() {
        String expUrlPolicies = RIC_1_URL + "/A1-P/v2/policytypes";
        String expUrlInstances = RIC_1_URL + "/A1-P/v2/policytypes/xxx/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrlPolicies, expUrlInstances);
    }

    @Test
    void getPolicyIdentities_OSC() {
        String expUrlTypes = RIC_1_URL + "/a1-p/policytypes";
        String expUrlInstances = RIC_1_URL + "/a1-p/policytypes/xxx/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrlTypes, expUrlInstances);
    }

    private void putPolicy(A1ProtocolType protocolType, String expUrl) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        whenPostReturnOkResponse();

        String returned = clientUnderTest
                .putPolicy(A1ClientHelper.createPolicy(RIC_1_URL, POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID))
                .block();

        assertEquals("OK", returned);
        AdapterRequest expectedInputParams = ImmutableAdapterRequest.builder() //
                .nearRtRicUrl(expUrl) //
                .body(POLICY_JSON_VALID) //
                .build();
        String expInput = SdncJsonHelper.createInputJsonString(expectedInputParams);

        verify(asyncRestClientMock).postWithAuthHeader(PUT_A1_URL, expInput, CONTROLLER_USERNAME, CONTROLLER_PASSWORD);

    }

    @Test
    void putPolicy_OSC() {
        String expUrl = RIC_1_URL + "/a1-p/policytypes/type1/policies/policy1";
        putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl);
    }

    @Test
    void putPolicy_STD_V1() {
        String expUrl = RIC_1_URL + "/A1-P/v1/policies/policy1";
        putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl);
    }

    @Test
    void putPolicy_STD_V2() {
        String expUrl =
                RIC_1_URL + "/A1-P/v2/policytypes/type1/policies/policy1?notificationDestination=https://test.com";
        putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl);
    }

    @Test
    void postRejected() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        final String policyJson = "{}";
        AdapterOutput adapterOutput = ImmutableAdapterOutput.builder() //
                .body("NOK") //
                .httpStatus(HttpStatus.BAD_REQUEST.value()) // ERROR
                .build();

        String resp = SdncJsonHelper.createOutputJsonString(adapterOutput);
        whenAsyncPostThenReturn(Mono.just(resp));

        Mono<String> returnedMono = clientUnderTest
                .putPolicy(A1ClientHelper.createPolicy(RIC_1_URL, POLICY_1_ID, policyJson, POLICY_TYPE_1_ID));
        StepVerifier.create(returnedMono) //
                .expectSubscription() //
                .expectErrorMatches(t -> t instanceof WebClientResponseException) //
                .verify();

        StepVerifier.create(returnedMono).expectErrorMatches(throwable -> {
            return throwable instanceof WebClientResponseException;
        }).verify();
    }

    private void deleteAllPolicies(A1ProtocolType protocolType, String expUrl) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);
        String resp = createOkResponseWithBody(Arrays.asList("xxx"));
        whenAsyncPostThenReturn(Mono.just(resp));

        clientUnderTest.deleteAllPolicies().blockLast();

        ImmutableAdapterRequest expectedParams = ImmutableAdapterRequest.builder() //
                .nearRtRicUrl(expUrl) //
                .build();
        String expInput = SdncJsonHelper.createInputJsonString(expectedParams);
        verify(asyncRestClientMock).postWithAuthHeader(DELETE_A1_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    void deleteAllPolicies_STD_V2() {
        String expUrl1 = RIC_1_URL + "/A1-P/v2/policytypes/xxx/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl1);
    }

    @Test
    void deleteAllPolicies_STD_V1() {
        String expUrl1 = RIC_1_URL + "/A1-P/v1/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl1);
    }

    @Test
    void deleteAllPolicies_OSC() {
        String expUrl1 = RIC_1_URL + "/a1-p/policytypes/xxx/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl1);
    }

    @Test
    void getVersion_OSC() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, // Version irrelevant here
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);

        whenAsyncPostThenReturn(Mono.error(new Exception("Error"))).thenReturn(Mono.just(createOkResponseString(true)));

        A1ProtocolType returnedVersion = clientUnderTest.getProtocolVersion().block();

        assertEquals(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, returnedVersion);
    }

    @Test
    void testGetStatus() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, //
                A1ClientHelper.createRic(RIC_1_URL).getConfig(), //
                controllerConfig(), asyncRestClientMock);
        whenPostReturnOkResponse();

        Policy policy = A1ClientHelper.createPolicy(RIC_1_URL, POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID);

        String response = clientUnderTest.getPolicyStatus(policy).block();
        assertEquals("OK", response);

        String expUrl = RIC_1_URL + "/A1-P/v2/policytypes/type1/policies/policy1/status";
        ImmutableAdapterRequest expectedParams = ImmutableAdapterRequest.builder() //
                .nearRtRicUrl(expUrl) //
                .build();
        String expInput = SdncJsonHelper.createInputJsonString(expectedParams);
        verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_STATUS_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);

    }

    private Gson gson() {
        return CcsdkA1AdapterClient.gson;
    }

    private String loadFile(String fileName) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource(fileName);
        File file = new File(url.getFile());
        return new String(Files.readAllBytes(file.toPath()));
    }

    private void whenPostReturnOkResponse() {
        whenAsyncPostThenReturn(Mono.just(createOkResponseString(true)));
    }

    void whenPostReturnOkResponseNoBody() {
        whenAsyncPostThenReturn(Mono.just(createOkResponseString(false)));
    }

    private String createOkResponseWithBody(Object body) {
        AdapterOutput output = ImmutableAdapterOutput.builder() //
                .body(gson().toJson(body)) //
                .httpStatus(HttpStatus.OK.value()) //
                .build();
        return SdncJsonHelper.createOutputJsonString(output);
    }

    private String createOkResponseString(boolean withBody) {
        Builder responseBuilder = ImmutableAdapterOutput.builder().httpStatus(HttpStatus.OK.value());
        if (withBody) {
            responseBuilder.body(HttpStatus.OK.name());
        } else {
            responseBuilder.body(Optional.empty());
        }
        return SdncJsonHelper.createOutputJsonString(responseBuilder.build());
    }

    private OngoingStubbing<Mono<String>> whenAsyncPostThenReturn(Mono<String> response) {
        return when(asyncRestClientMock.postWithAuthHeader(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);
    }
}
