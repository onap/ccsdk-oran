package org.onap.ccsdk.oran.a1policymanagementservice.utils.v3;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.OpenPolicyAgentSimulatorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({OutputCaptureExtension.class})
@TestPropertySource(properties = {
        "server.ssl.key-store=./config/keystore.jks",
        "app.webclient.trust-store=./config/truststore.jks",
        "app.vardata-directory=./target",
        "app.config-file-schema-path=/application_configuration_schema.json",
        "logging.reactive-entry-exit-filter-enabled=true",
        "logging.level.org.onap.ccsdk.oran.a1policymanagementservice=TRACE"
})
class ReactiveEntryExitFilterTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

    @AfterAll
    static void clearTestDir() {
        try {
            FileSystemUtils.deleteRecursively(Path.of("/tmp/pmstestv3"));
        } catch (Exception e) {
            logger.warn("Could test directory : {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("test verify entry exit log")
    void testPostPolicy(CapturedOutput capturedOutput) throws Exception {
        String nonRtRicId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String url = "/policies";
        testHelperTest.addPolicyType(policyTypeName, nonRtRicId);
        String policyBody = testHelperTest.postPolicyBody(nonRtRicId, policyTypeName, "");
        Mono<ResponseEntity<String>> responseMono = testHelperTest.restClientV3().postForEntity(url, policyBody);
        testHelperTest.testSuccessResponse(responseMono, HttpStatus.CREATED, responseBody ->
                responseBody.contains("{\"scope\":{\"ueId\":\"ue5100\",\"qosId\":\"qos5100\"},\"qosObjectives\":{\"priorityLevel\":5100.0}}"));
        testHelperTest.testSuccessHeader(responseMono, "location", headerValue -> headerValue.contains("https://localhost:" + port + "/a1-policy-management/v1/policies/"));
        assertTrue(capturedOutput.getOut().contains("Request received with path: /a1-policy-management/v1/policies"));
        assertTrue(capturedOutput.getOut().contains("the Status code of the response: 201 CREATED"));
        assertTrue(capturedOutput.getOut().contains("the response is:"));
    }
}
