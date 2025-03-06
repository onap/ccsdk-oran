/*-
 * ========================LICENSE_START=================================
 * Copyright (C) 2020-2025 OpenInfra Foundation Europe. All rights reserved.
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
import org.junit.jupiter.api.io.TempDir;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelperTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.io.File;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Objects;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.vardata-directory=./target", //
        "app.config-file-schema-path=/application_configuration_schema.json" //
})
class ConfigurationControllerV3Test {

    @Autowired
    private RefreshConfigTask refreshConfigTask;

    @Autowired
    ApplicationContext context;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    private Rics rics;

    @Autowired
    private TestHelperTest testHelperTest;

    @TempDir
    public static File temporaryFolder;
    private static File configFile;

    @LocalServerPort
    private int port;

    @BeforeEach
    void init() {
        testHelperTest.port = port;
    }
    @BeforeAll
    static void setup() throws Exception {
        Field f1 = RefreshConfigTask.class.getDeclaredField("configRefreshInterval");
        f1.setAccessible(true);
        f1.set(null, Duration.ofSeconds(1));
    }

    public static class MockApplicationConfig extends ApplicationConfig {
        @Override
        public String getLocalConfigurationFilePath() {
            configFile = new File(temporaryFolder, "config.json");
            return configFile.getAbsolutePath();
        }
    }

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory {
        @Bean
        public ApplicationConfig getApplicationConfig() {
            return new MockApplicationConfig();
        }

        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }
    }

    @Test
    void testPutConfiguration() throws Exception {
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().putForEntity("/configuration",
                testHelperTest.configAsString());
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.OK, Objects::isNull);
        //put Valid Configuration With New Ric should Update Repository. So, will wait until the ric size is 2
        await().until(rics::size, equalTo(2));
        //test Get Configuration
        Mono<ResponseEntity<String>> responseGetConfigMono = testHelperTest.restClientV3().getForEntity("/configuration");
        testHelperTest.testSuccessResponse(responseGetConfigMono, HttpStatus.OK, responseBody -> responseBody.contains("config"));
    }

    @Test
    void testPutConfigurationMeNull() throws Exception {
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().putForEntity("/configuration",
                testHelperTest.configAsStringMeNull());
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.OK, Objects::isNull);
        //put Valid Configuration With New Ric should Update Repository. So, will wait until the ric size is 2
        await().until(rics::size, equalTo(2));
        //test Get Configuration
        Mono<ResponseEntity<String>> responseGetConfigMono = testHelperTest.restClientV3().getForEntity("/configuration");
        testHelperTest.testSuccessResponse(responseGetConfigMono, HttpStatus.OK, responseBody -> !responseBody.contains("\"managedElementIds\""));

        refreshConfigTask.start();

        Mono<ResponseEntity<String>> responseGetRicsMono = testHelperTest.restClientV3().getForEntity("/rics");
        testHelperTest.testSuccessResponse(responseGetRicsMono, HttpStatus.OK, responseBody -> responseBody.contains("\"managedElementIds\":[]"));

        Mono<ResponseEntity<String>> responseGetRicMono = testHelperTest.restClientV3().getForEntity("/rics/ric1");
        testHelperTest.testSuccessResponse(responseGetRicMono, HttpStatus.OK, responseBody -> responseBody.contains("\"managedElementIds\":[]"));
    }

    @Test
    void testPutConfigurationMeEmpty() throws Exception {
        Mono<ResponseEntity<String>> responseEntityMono = testHelperTest.restClientV3().putForEntity("/configuration",
                testHelperTest.configAsStringMeEmpty());
        testHelperTest.testSuccessResponse(responseEntityMono, HttpStatus.OK, Objects::isNull);
        //put Valid Configuration With New Ric should Update Repository. So, will wait until the ric size is 2
        await().until(rics::size, equalTo(2));
        //test Get Configuration
        Mono<ResponseEntity<String>> responseGetConfigMono = testHelperTest.restClientV3().getForEntity("/configuration");
        testHelperTest.testSuccessResponse(responseGetConfigMono, HttpStatus.OK, responseBody -> responseBody.contains("\"managedElementIds\":[]"));

        refreshConfigTask.start();

        Mono<ResponseEntity<String>> responseGetRicsMono = testHelperTest.restClientV3().getForEntity("/rics");
        testHelperTest.testSuccessResponse(responseGetRicsMono, HttpStatus.OK, responseBody -> responseBody.contains("\"managedElementIds\":[]"));

        Mono<ResponseEntity<String>> responseGetRicMono = testHelperTest.restClientV3().getForEntity("/rics/ric1");
        testHelperTest.testSuccessResponse(responseGetRicMono, HttpStatus.OK, responseBody -> responseBody.contains("\"managedElementIds\":[]"));
    }

    @Test
    void testHealthCheck() {
        Mono<ResponseEntity<String>> responseHealthCheckMono = testHelperTest.restClientV3().getForEntity("/status");
        testHelperTest.testSuccessResponse(responseHealthCheckMono, HttpStatus.OK, responseBody -> responseBody.contains("status"));
    }
}
