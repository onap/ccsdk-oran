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
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.CcsdkA1AdapterClient.AdapterOutput;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.CcsdkA1AdapterClient.AdapterRequest;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CcsdkA1AdapterClientTest {
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
        return ControllerConfig.builder() //
                .name("name") //
                .baseUrl("baseUrl") //
                .password(CONTROLLER_PASSWORD) //
                .userName(CONTROLLER_USERNAME) //
                .build();
    }

    @Test
    @DisplayName("test create Client With Wrong Protocol then Error Is Thrown")
    void createClientWithWrongProtocol_thenErrorIsThrown() {
        AsyncRestClient asyncRestClient = new AsyncRestClient("", null, null, new SecurityContext(""));
        assertThrows(IllegalArgumentException.class, () -> {
            new CcsdkA1AdapterClient(A1ProtocolType.STD_V1_1, null, asyncRestClient);
        });
    }

    @Test
    @DisplayName("test get Policy Type Identities STD V1")
    void getPolicyTypeIdentities_STD_V1() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);
        List<String> policyTypeIds = clientUnderTest.getPolicyTypeIdentities().block();
        assertEquals(1, policyTypeIds.size(), "should hardcoded to one");
        assertEquals("", policyTypeIds.get(0), "should hardcoded to empty");
    }

    private Ric createRic(String url) {
        RicConfig cfg = RicConfig.builder().ricId("ric") //
                .baseUrl(url) //
                .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))) //
                .controllerConfig(controllerConfig()) //
                .build();
        return new Ric(cfg);
    }

    private void testGetPolicyTypeIdentities(A1ProtocolType protocolType, String expUrl) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        String response = createOkResponseWithBody(Arrays.asList(POLICY_TYPE_1_ID));
        whenAsyncPostThenReturn(Mono.just(response));

        List<String> policyTypeIds = clientUnderTest.getPolicyTypeIdentities().block();

        assertEquals(1, policyTypeIds.size());
        assertEquals(POLICY_TYPE_1_ID, policyTypeIds.get(0));

        AdapterRequest expectedParams = new AdapterRequest(expUrl, null);

        String expInput = A1AdapterJsonHelper.createInputJsonString(expectedParams);
        verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    @DisplayName("test get Policy Type Identities OSC")
    void getPolicyTypeIdentities_OSC() {
        testGetPolicyTypeIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, RIC_1_URL + "/a1-p/policytypes");
    }

    @Test
    @DisplayName("test get Policy Type Identities STD V2")
    void getPolicyTypeIdentities_STD_V2() {
        testGetPolicyTypeIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, RIC_1_URL + "/A1-P/v2/policytypes");
    }

    @Test
    @DisplayName("test get Type Schema STD V1")
    void getTypeSchema_STD_V1() {

        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        String policyType = clientUnderTest.getPolicyTypeSchema("").block();

        assertEquals("{}", policyType);
    }

    private void testGetTypeSchema(A1ProtocolType protocolType, String expUrl, String policyTypeId,
            String getSchemaResponseFile) throws IOException {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        String ricResponse = loadFile(getSchemaResponseFile);
        JsonElement elem = gson().fromJson(ricResponse, JsonElement.class);
        String responseFromController = createOkResponseWithBody(elem);
        whenAsyncPostThenReturn(Mono.just(responseFromController));

        String response = clientUnderTest.getPolicyTypeSchema(policyTypeId).block();

        JsonElement respJson = gson().fromJson(response, JsonElement.class);
        assertEquals(policyTypeId, respJson.getAsJsonObject().get("title").getAsString(),
                "title should be updated to contain policyType ID");

        AdapterRequest expectedParams = new AdapterRequest(expUrl, null);

        String expInput = A1AdapterJsonHelper.createInputJsonString(expectedParams);

        verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    @DisplayName("test get Type Schema OSC")
    void getTypeSchema_OSC() throws IOException {
        String expUrl = RIC_1_URL + "/a1-p/policytypes/policyTypeId";
        testGetTypeSchema(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl, "policyTypeId",
                "test_osc_get_schema_response.json");
    }

    @Test
    @DisplayName("test get Type Schema STD V2")
    void getTypeSchema_STD_V2() throws IOException {
        String expUrl = RIC_1_URL + "/A1-P/v2/policytypes/policyTypeId";
        testGetTypeSchema(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl, "policyTypeId",
                "test_oran_get_schema_response.json");
    }

    @Test
    @DisplayName("test parse Json Array Of String")
    void parseJsonArrayOfString() {
        // One integer and one string
        String inputString = "[1, \"1\" ]";

        List<String> result = A1AdapterJsonHelper.parseJsonArrayOfString(inputString).collectList().block();
        assertEquals(2, result.size());
        assertEquals("1", result.get(0));
        assertEquals("1", result.get(1));
    }

    private void getPolicyIdentities(A1ProtocolType protocolType, String... expUrls) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);
        String resp = createOkResponseWithBody(Arrays.asList("xxx"));
        whenAsyncPostThenReturn(Mono.just(resp));

        List<String> returned = clientUnderTest.getPolicyIdentities().block();

        assertEquals(1, returned.size());
        for (String expUrl : expUrls) {
            AdapterRequest expectedParams = new AdapterRequest(expUrl, null);

            String expInput = A1AdapterJsonHelper.createInputJsonString(expectedParams);
            verify(asyncRestClientMock).postWithAuthHeader(GET_A1_POLICY_URL, expInput, CONTROLLER_USERNAME,
                    CONTROLLER_PASSWORD);
        }
    }

    @Test
    @DisplayName("test get Policy Identities STD V1")
    void getPolicyIdentities_STD_V1() {
        String expUrl = RIC_1_URL + "/A1-P/v1/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl);
    }

    @Test
    @DisplayName("test get Policy Identities STD V2")
    void getPolicyIdentities_STD_V2() {
        String expUrlPolicies = RIC_1_URL + "/A1-P/v2/policytypes";
        String expUrlInstances = RIC_1_URL + "/A1-P/v2/policytypes/xxx/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrlPolicies, expUrlInstances);
    }

    @Test
    @DisplayName("test get Policy Identities OSC")
    void getPolicyIdentities_OSC() {
        String expUrlTypes = RIC_1_URL + "/a1-p/policytypes";
        String expUrlInstances = RIC_1_URL + "/a1-p/policytypes/xxx/policies";
        getPolicyIdentities(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrlTypes, expUrlInstances);
    }

    private void putPolicy(A1ProtocolType protocolType, String expUrl) {
        clientUnderTest = new CcsdkA1AdapterClient(protocolType, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        whenPostReturnOkResponse();

        String returned = clientUnderTest
                .putPolicy(A1ClientHelper.createPolicy(RIC_1_URL, POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID))
                .block();

        assertEquals("OK", returned);
        AdapterRequest expectedInputParams = new AdapterRequest(expUrl , POLICY_JSON_VALID);
        String expInput = A1AdapterJsonHelper.createInputJsonString(expectedInputParams);

        verify(asyncRestClientMock).postWithAuthHeader(PUT_A1_URL , expInput, CONTROLLER_USERNAME, CONTROLLER_PASSWORD);

    }

    @Test
    @DisplayName("test put Policy OSC")
    void putPolicy_OSC() {
        String expUrl = RIC_1_URL + "/a1-p/policytypes/type1/policies/policy1?notificationDestination\u003dhttps%3A%2F%2Ftest.com";
	putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl);
    }

    @Test
    @DisplayName("test put Policy STD V1")
    void putPolicy_STD_V1() {
        String expUrl = RIC_1_URL + "/A1-P/v1/policies/policy1";
        putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl);
    }

    @Test
    @DisplayName("test put Policy STD V2")
    void putPolicy_STD_V2() {
        String expUrl =
                RIC_1_URL + "/A1-P/v2/policytypes/type1/policies/policy1?notificationDestination=https://test.com";
        putPolicy(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl);
    }

    @Test
    @DisplayName("test post Rejected")
    void postRejected() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        final String policyJson = "{}";
        AdapterOutput adapterOutput = new AdapterOutput(HttpStatus.BAD_REQUEST.value(), "NOK");

        String resp = A1AdapterJsonHelper.createOutputJsonString(adapterOutput);
        whenAsyncPostThenReturn(Mono.just(resp));

        Mono<String> returnedMono = clientUnderTest
                .putPolicy(A1ClientHelper.createPolicy(RIC_1_URL +  "?notificationDestination=https%3A%2F%2Ftest.com", POLICY_1_ID, policyJson, POLICY_TYPE_1_ID));
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
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);
        String resp = createOkResponseWithBody(Arrays.asList("xxx"));
        whenAsyncPostThenReturn(Mono.just(resp));

        clientUnderTest.deleteAllPolicies().blockLast();

        AdapterRequest expectedParams = new AdapterRequest(expUrl, null);

        String expInput = A1AdapterJsonHelper.createInputJsonString(expectedParams);
        verify(asyncRestClientMock).postWithAuthHeader(DELETE_A1_URL, expInput, CONTROLLER_USERNAME,
                CONTROLLER_PASSWORD);
    }

    @Test
    @DisplayName("test delete All Policies STD V2")
    void deleteAllPolicies_STD_V2() {
        String expUrl1 = RIC_1_URL + "/A1-P/v2/policytypes/xxx/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, expUrl1);
    }

    @Test
    @DisplayName("test delete All Policies STD V1")
    void deleteAllPolicies_STD_V1() {
        String expUrl1 = RIC_1_URL + "/A1-P/v1/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, expUrl1);
    }

    @Test
    @DisplayName("test delete All Policies OSC")
    void deleteAllPolicies_OSC() {
        String expUrl1 = RIC_1_URL + "/a1-p/policytypes/xxx/policies/xxx";
        deleteAllPolicies(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, expUrl1);
    }

    @Test
    @DisplayName("test get Version OSC")
    void getVersion_OSC() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1, // Version irrelevant here
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);

        whenAsyncPostThenReturn(Mono.error(new Exception("Error"))).thenReturn(Mono.just(createOkResponseString(true)));

        A1ProtocolType returnedVersion = clientUnderTest.getProtocolVersion().block();

        assertEquals(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1, returnedVersion);
    }

    @Test
    @DisplayName("test Get Status")
    void testGetStatus() {
        clientUnderTest = new CcsdkA1AdapterClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0, //
                createRic(RIC_1_URL).getConfig(), //
                asyncRestClientMock);
        whenPostReturnOkResponse();

        Policy policy = A1ClientHelper.createPolicy(RIC_1_URL + "?notificationDestination=https%3A%2F%2Ftest.com", POLICY_1_ID, POLICY_JSON_VALID, POLICY_TYPE_1_ID);

        String response = clientUnderTest.getPolicyStatus(policy).block();
        assertEquals("OK", response);

        String expUrl = RIC_1_URL + "/A1-P/v2/policytypes/type1/policies/policy1/status";
        AdapterRequest expectedParams = new AdapterRequest(expUrl, null);

        String expInput = A1AdapterJsonHelper.createInputJsonString(expectedParams);
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
        AdapterOutput output = new AdapterOutput(HttpStatus.OK.value(), gson().toJson(body));
        return A1AdapterJsonHelper.createOutputJsonString(output);
    }

    private String createOkResponseString(boolean withBody) {
        String body = withBody ? HttpStatus.OK.name() : null;
        AdapterOutput output = new AdapterOutput(HttpStatus.OK.value(), body);
        return A1AdapterJsonHelper.createOutputJsonString(output);
    }

    private OngoingStubbing<Mono<String>> whenAsyncPostThenReturn(Mono<String> response) {
        return when(asyncRestClientMock.postWithAuthHeader(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);
    }
}
