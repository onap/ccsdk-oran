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

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.config.TestConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.OpenPolicyAgentSimulatorController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.RappSimulatorController;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ServiceRegistrationInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
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
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.time.Duration;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.webclient.trust-store-used=true", //
        "app.vardata-directory=/tmp/pmstestv3", //a
        "app.filepath=", //
        "app.s3.bucket=" // If this is set, S3 will be used to store data.
})
class ServiceControllerV3Test {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private TestHelperTest testHelperTest;

    @Autowired
    private ApplicationConfig applicationConfig;

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

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    private Gson gson;

    @LocalServerPort
    private int port;

    @BeforeEach
    void init() {
        testHelperTest.port = port;
        this.applicationConfig.setAuthProviderUrl(testHelperTest.baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL);
    }

    @AfterEach
    void reset() {
        services.clear();
        a1ClientFactory.reset();
        this.rAppSimulator.getTestResults().clear();
        this.a1ClientFactory.setPolicyTypes(policyTypes); // Default same types in RIC and in this app
        this.securityContext.setAuthTokenFilePath(null);
        this.openPolicyAgentSimulatorController.getTestResults().reset();
    }

    @Test
    void testPutService() {
        ServiceRegistrationInfo serviceRegistrationInfo = new ServiceRegistrationInfo("serviceId");
        serviceRegistrationInfo.callbackUrl("http://callback.com/").keepAliveIntervalSeconds(10L);
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3()
                .putForEntity("/services", gson.toJson(serviceRegistrationInfo));
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.CREATED, responseBody -> services.size() == 1);
    }

    @Test
    void testGetService() {
        services.put(new Service("newServiceId", Duration.ofSeconds(10L),  "http://callback.com/"));
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().getForEntity("/services");
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBoy -> responseBoy
                .contains("http://callback.com/"));
    }

    @Test
    void testDeleteService() {
        services.put(new Service("newServiceId", Duration.ofSeconds(10L),  "http://callback.com/"));
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().deleteForEntity("/services/newServiceId");
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.NO_CONTENT, responseBody -> services.size() == 0);
    }

    @Test
    void testKeepAliveService() {
        services.put(new Service("newServiceId", Duration.ofSeconds(10L),  "http://callback.com/"));
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().putForEntity("/services/newServiceId/keepalive", "");
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> services.size() == 1);
    }
}
