/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v3;

import org.junit.jupiter.api.*;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.config.TestConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.OpenPolicyAgentSimulatorController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.RappSimulatorController;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelperTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.webclient.trust-store-used=true", //
        "app.vardata-directory=/tmp/pmstestv3", //
        "app.filepath=", //
        "app.s3.bucket=" // If this is set, S3 will be used to store data.
})
class PolicyControllerV3Test {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private TestHelperTest testHelperTest;

    @Autowired
    private Rics rics;

    @Autowired
    private Policies policies;

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    private Services services;

    @Autowired
    private MockA1ClientFactory a1ClientFactory;

    @Autowired
    private RappSimulatorController rAppSimulator;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private OpenPolicyAgentSimulatorController openPolicyAgentSimulatorController;

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private Helper helper;

    private final String bearerToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
    + "eyJpc3MiOiJleGFtcGxlX2lzc3VlciIsInN1YiI6IjEyMzQ1Njc4OTAiLCJhdWQiOiJteWNsaWVudCIs"
    + "ImV4cCI6MzAwMDAwMDAwMCwiY2xpZW50X2lkIjoibXljbGllbnQiLCJyb2xlIjoidXNlciJ9."
    + "O5QN_SWN4J1mWKyXk_-PCvOA6GF3ypv1rSdg2uTb_Ls";

    private final String emptyBearerToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IiJ9."
    + "eyJpYXQiOjE1MTYyMzkwMjJ9.uE72OfhNzhIFuyHhZyI0eYVPG6QJ7s7A-SVeKsLubCQ";

    @BeforeEach
    void init() {
        testHelperTest.port = port;
        this.applicationConfig.setAuthProviderUrl(testHelperTest.baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL);
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.NONE);
    }

    @AfterEach
    void reset() {
        rics.clear();
        policies.clear();
        policyTypes.clear();
        services.clear();
        a1ClientFactory.reset();
        this.rAppSimulator.getTestResults().clear();
        this.a1ClientFactory.setPolicyTypes(policyTypes); // Default same types in RIC and in this app
        this.securityContext.setAuthTokenFilePath(null);
        this.openPolicyAgentSimulatorController.getTestResults().reset();
    }

    @AfterAll
    static void clearTestDir() {
        try {
            FileSystemUtils.deleteRecursively(Path.of("/tmp/pmstestv3"));
        } catch (Exception e) {
            logger.warn("Could test directory : {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("test Create Policy")
    void testPostPolicy() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.CREATED, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }


    @Test
    @DisplayName("test Create Policy Success when schema validation set to FAIL")
    void testPolicyTypeSchemaValidationFail() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.FAIL);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.CREATED, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }


    @Test
    @DisplayName("test Create Policy Success when schema validation set to INFO")
    void testPolicyTypeSchemaValidationInfo() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.INFO);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.CREATED, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }


    @Test
    @DisplayName("test Create Policy Success when schema validation set to WARN")
    void testPolicyTypeSchemaValidationWarn() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.WARN);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.CREATED, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }

    @Test
    @DisplayName("test bad Create Policy when schema validation set to FAIL")
    void testBadPolicyTypeSchemaValidationFail() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.FAIL);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postBadPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testErrorCode(responseMono, HttpStatus.BAD_REQUEST, "Policy Type Schema validation failed");
    }

    @Test
    @DisplayName("test bad Create Policy when schema validation set to INFO")
    void testBadPolicyTypeSchemaValidationInfo() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.INFO);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postBadPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }

    @Test
    @DisplayName("test bad Create Policy when schema validation set to WARN")
    void testBadPolicyTypeSchemaValidationWarn() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.WARN);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postBadPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
    }

    @Test
    @DisplayName("test Create Policy with PolicyID sending")
    void testPostPolicyWithPolicyID() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "1");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/1"));
    }

    @Test
    @DisplayName("test Create Policy with existing policy id")
    void testPostPolicyWithExistingPolicyID() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        String policyId = "policy_5g";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, policyId);
        testHelperTest.restClientV3().postForEntity(url, policyBody).block();
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testErrorCode(responseMono, HttpStatus.CONFLICT, "Policy already created with ID: " +policyId);
    }

    @Test
    @DisplayName("test delete Policy")
    void testDeletePolicy() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        String []locationHeader = Objects.requireNonNull(Objects.requireNonNull(responseMono.block()).getHeaders()
                .get("location")).get(0).split("/");
        String policyID = locationHeader[(locationHeader.length) - 1];
        Mono<ResponseEntity<String>> responseMonoDelete = testHelperTest.restClientV3().deleteForEntity(url+"/" +policyID);
        testHelperTest.testSuccessResponse(responseMonoDelete, HttpStatus.NO_CONTENT, responseBody -> true);
    }

    @Test
    @DisplayName("test Create Policy schema validation fail case")
    void testPolicySchemaValidationFail() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.FALSE);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testErrorCode(responseMono, HttpStatus.BAD_REQUEST, " Schema validation failed");
    }

    @Test
    @DisplayName("test Create Policy No Ric fail case")
    void testCreatePolicyNoRic() throws Exception {
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, " ");
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        String policyBody = testHelperTest.postPolicyBody("noRic", policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testErrorCode(responseMono, HttpStatus.NOT_FOUND, " Could not find ric: noRic");
    }

    @Test
    @DisplayName("test Create Policy with No Policy Type fail case")
    void testCreatePolicyNoPolicyType() throws Exception {
        String policyTypeName = "type1_1.2.3";
        String nonRtRicId = "ricOne";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, "noPolicyType", "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testErrorCode(responseMono, HttpStatus.NOT_FOUND, "Could not find type: noPolicyType");
    }

    @Test
    void testGetPolicyTypesNoRicFound() throws Exception{
        String policyTypeName = "type1_1.2.3";
        String nonRtRicId = "ricOne";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().getForEntity("/policy-types" + "?nearRtRicId=\"noRic\"");
        testHelperTest.testErrorCode(responseMono, HttpStatus.NOT_FOUND, "Near-RT RIC not Found using ID:");
    }

    @Test
    @DisplayName("test get Policy")
    void testGetPolicy() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        String []locationHeader = Objects.requireNonNull(Objects.requireNonNull(responseMono.block()).getHeaders()
                .get("location")).get(0).split("/");
        String policyID = locationHeader[(locationHeader.length) - 1];
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/" +policyID);
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
    }

    @Test
    @DisplayName("test get all Policies")
    void testGetAllPolicies() throws Exception {
        String nonRtRicIdOne = "ric.11";
        String nonRtRicIdTwo = "ric.22";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicIdOne);
        String policyBodyOne = testHelperTest.postPolicyBody(nonRtRicIdOne, policyTypeName, "policyOne");
        testHelperTest.addPolicyType(policyTypeName, nonRtRicIdTwo);
        String policyBodyTwo = testHelperTest.postPolicyBody(nonRtRicIdTwo, policyTypeName, "policyTwo");
        testHelperTest.restClientV3().postForEntity(url, policyBodyOne).block();
        testHelperTest.restClientV3().postForEntity(url, policyBodyTwo).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url);
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("[{\"policyId\":\"policyTwo\",\"nearRtRicId\":\"ric.22\"},{\"policyId\":\"policyOne\",\"nearRtRicId\":\"ric.11\"}]"));
}

    @Test
    @DisplayName("test get PolicyType")
    void testGetPolicyType() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policy-types";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/" +policyTypeName);
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody -> !(responseBody.isEmpty()));
    }

    @Test
    @DisplayName("test get All PolicyTypes")
    void testGetAllPolicyTypes() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policy-types";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url);
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody -> responseBody.contains(
                "{\"policyTypeId\":\"type1_1.2.3\",\"nearRtRicId\":\"ric.1\"}]"
        ));
    }

    @Test
    @DisplayName("test update Policy")
    void testUpdatePolicy() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0");
        testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/policyOne");
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\"},\"qosObjectives\":{\"priorityLevel\":5200.0}"));
    }


    @Test
    @DisplayName("test Update Policy Success when schema validation set to FAIL")
    void testUpdatePolicyTypeSchemaValidationFail() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.FAIL);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0");
        testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/policyOne");
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\"},\"qosObjectives\":{\"priorityLevel\":5200.0}"));
    }


    @Test
    @DisplayName("test Update Policy Success when schema validation set to INFO")
    void testUpdatePolicyTypeSchemaValidationInfo() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.INFO);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0");
        testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/policyOne");
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\"},\"qosObjectives\":{\"priorityLevel\":5200.0}"));
    }


    @Test
    @DisplayName("test Update Policy Success when schema validation set to WARN")
    void testUpdatePolicyTypeSchemaValidationWarn() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.WARN);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0");
        testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/policyOne");
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\"},\"qosObjectives\":{\"priorityLevel\":5200.0}"));
    }

    @Test
    @DisplayName("test bad Update Policy when schema validation set to FAIL")
    void testUpdateBadPolicyTypeSchemaValidationFail() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.FAIL);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putBadPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0", "bar");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut);
        testHelperTest.testErrorCode(responseMono, HttpStatus.BAD_REQUEST, "Policy Type Schema validation failed");
    }

    @Test
    @DisplayName("test bad Update Policy when schema validation set to WARN")
    void testUpdateBadPolicyTypeSchemaValidationWarn() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.WARN);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putBadPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0", "bar");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\",\"foo\":\"bar\"},\"qosObjectives\":{\"priorityLevel\":5200.0}}"));
    }

    @Test
    @DisplayName("test bad Update Policy when schema validation set to INFO")
    void testUpdateBadPolicyTypeSchemaValidationInfo() throws Exception {
        this.applicationConfig.setValidatePolicyInstanceSchema(ApplicationConfig.ValidateSchema.INFO);
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "policyOne");
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        String policyBodyForPut = testHelperTest.putBadPolicyBody(nonRtRicId, policyTypeName, "policyOne", "ue5200",
                "qos5200", "5200.0", "bar");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().putForEntity(url+"/policyOne", policyBodyForPut);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.OK, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5200\",\"qosId\":\"qos5200\",\"foo\":\"bar\"},\"qosObjectives\":{\"priorityLevel\":5200.0}}"));
    }

    private void postPolicyWithTokenAndVerify(String clientId, String serviceId, String result) throws IOException {
        testHelperTest.addPolicyType("type1_1.2.3", "ric.1");
        String policyBody = testHelperTest.postPolicyBody("ric.1", "type1_1.2.3", "1");

        if (serviceId != null) {
            policyBody = policyBody.replace("\"serviceId\":\"\"", "\"serviceId\":\"" + serviceId + "\"");
        }

        StepVerifier.create(testHelperTest.restClientV3().postWithToken("/policies", policyBody, clientId)
                    .then(testHelperTest.restClientV3().getForEntity("/policies" + ((serviceId != null || clientId != null) ? "?serviceId=" + result : ""))))
                    .expectNextMatches(response -> response.getBody().contains("\"policyId\":\"1\""))
                    .expectComplete()
                    .verify();
    }

    @Test
    @DisplayName("client_id VALID + service_id NULL/EMPTY = client_id")
    void testPostPolicyWithToken() throws IOException {
        postPolicyWithTokenAndVerify(bearerToken, null, "myclient");
    }

    @Test
    @DisplayName("client_id VALID + service_id VALID = service_id")
    void testPostPolicyWithTokenAndServiceID() throws IOException {
        postPolicyWithTokenAndVerify(bearerToken, "notmyclient", "notmyclient");
    }

    @Test
    @DisplayName("client_id NULL + service_id EMPTY = empty")
    void testClientIdNullServiceIdEmpty() throws Exception {
        postPolicyWithTokenAndVerify(null, null, "");
    }

    @Test
    @DisplayName("client_id NULL + service_id VALID = service_id")
    void testClientIdNullServiceIdValid() throws Exception {
        postPolicyWithTokenAndVerify(null, "validServiceId", "validServiceId");
    }

    @Test
    @DisplayName("client_id EMPTY + service_id NULL/EMPTY = empty")
    void testClientIdEmptyServiceIdEmpty() throws Exception {
        postPolicyWithTokenAndVerify(emptyBearerToken, null, "");
    }

    @Test
    @DisplayName("client_id EMPTY + service_id VALID = service_id")
    void testEmptyClientIdServiceIdValid() throws Exception {
        postPolicyWithTokenAndVerify(emptyBearerToken, "validServiceId", "validServiceId");
    }

    @Test
    @DisplayName("test get Policy Status")
    void testGetPolicyStatus() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        String policyId = "policyOne";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBodyForPost = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, policyId);
        testHelperTest.restClientV3().postForEntity(url, policyBodyForPost).block();
        Mono<ResponseEntity<String>> responseMonoGet = testHelperTest.restClientV3().getForEntity(url+"/"+ policyId +"/status");
        testHelperTest.testSuccessResponse(responseMonoGet, HttpStatus.OK, responseBody ->
                responseBody.contains("OK"));
    }
}
