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
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelper;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1ClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

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
public class PolicyControllerTest {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private TestHelper testHelper;

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

    @SpyBean
    private Helper helper;

    @BeforeEach
    void init() {
        testHelper.port = port;
        this.applicationConfig.setAuthProviderUrl(testHelper.baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL);
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
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelper.postPolicyBody(nonRtRicId, policyTypeName);
        Mono<ResponseEntity<String>> responseMono = testHelper.restClientV3().postForEntity(url, policyBody);
        testHelper.testSuccessResponse(responseMono, HttpStatus.CREATED, "{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}");
    }

    @Test
    @DisplayName("test Create Policy schema validation fail case")
    void testPolicySchemaValidationFail() throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.FALSE);
        String policyBody = testHelper.postPolicyBody(nonRtRicId, policyTypeName);
        Mono<ResponseEntity<String>> responseMono = testHelper.restClientV3().postForEntity(url, policyBody);
        testHelper.testErrorCode(responseMono, HttpStatus.BAD_REQUEST, " Schema validation failed");
    }

    @Test
    @DisplayName("test Create Policy No Ric fail case")
    void testCreatePolicyNoRic() throws Exception {
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelper.addPolicyType(policyTypeName, " ");
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        String policyBody = testHelper.postPolicyBody("noRic", policyTypeName);
        Mono<ResponseEntity<String>> responseMono = testHelper.restClientV3().postForEntity(url, policyBody);
        testHelper.testErrorCode(responseMono, HttpStatus.NOT_FOUND, " Could not find ric: noRic");
    }

    @Test
    @DisplayName("test Create Policy with No Policy Type fail case")
    void testCreatePolicyNoPolicyType() throws Exception {
        String policyTypeName = "type1_1.2.3";
        String nonRtRicId = "ricOne";
        String url = "/policies";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        String policyBody = testHelper.postPolicyBody(nonRtRicId, "noPolicyType");
        Mono<ResponseEntity<String>> responseMono = testHelper.restClientV3().postForEntity(url, policyBody);
        testHelper.testErrorCode(responseMono, HttpStatus.NOT_FOUND, "Could not find type: noPolicyType");
    }
}
