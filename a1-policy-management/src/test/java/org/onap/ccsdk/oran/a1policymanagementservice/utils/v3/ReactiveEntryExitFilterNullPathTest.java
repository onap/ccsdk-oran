/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2025 OpenInfra Foundation Europe. All rights reserved.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.OpenPolicyAgentSimulatorController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({OutputCaptureExtension.class})
@TestPropertySource(properties = {
        "server.ssl.key-store=./config/keystore.jks",
        "app.webclient.trust-store=./config/truststore.jks",
        "app.vardata-directory=./target",
        "app.config-file-schema-path=/application_configuration_schema.json",
        "logging.reactive-entry-exit-filter-enabled=true",
        "logging.level.org.onap.ccsdk.oran.a1policymanagementservice=TRACE",
        "logging.reactive-entry-exit-filter-exclude-paths=null"
})
class ReactiveEntryExitFilterNullPathTest {
    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private TestHelperTest testHelperTest;

    @LocalServerPort
    private int port;

    @BeforeEach
    void init() {
        testHelperTest.port = port;
        this.applicationConfig.setAuthProviderUrl(testHelperTest.baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL);
    }

    @Test
    @DisplayName("test verify entry exit log for health actuator is present")
    void testHealthActuatorFilterOmitted(CapturedOutput capturedOutput) {
        String url = "/actuator/health";
        Mono<ResponseEntity<String>> responseGetHealthMono =
                testHelperTest.restClient(testHelperTest.baseUrl(), false).getForEntity(url);
        testHelperTest.testSuccessResponse(responseGetHealthMono, HttpStatus.OK, responseBody -> responseBody.contains("UP"));
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> {
                assertTrue(capturedOutput.getOut().contains("Request received with path: /actuator/health"));
                assertTrue(capturedOutput.getOut().contains("the response is:"));
            });
    }
}
