/*-
 * ========================LICENSE_START=================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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
import org.junit.jupiter.api.io.TempDir;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.vardata-directory=./target", //
        "app.config-file-schema-path=/application_configuration_schema.json" //
})
class ConfigurationControllerV3Test {
    @Autowired
    ApplicationContext context;

    @SpyBean
    ApplicationConfig applicationConfig;

    @Autowired
    private Rics rics;

    @Autowired
    private TestHelper testHelper;

    @TempDir
    public static File temporaryFolder;
    private static File configFile;

    @LocalServerPort
    private int port;

    @BeforeEach
    void init() {
        testHelper.port = port;
    }
    @BeforeAll
    static void setup() throws Exception {
        Field f1 = RefreshConfigTask.class.getDeclaredField("configRefreshInterval");
        f1.setAccessible(true);
        f1.set(null, Duration.ofSeconds(1));
        configFile = new File(temporaryFolder, "config.json");
    }

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory {
        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }
    }

    @Test
    void testPutConfiguration() throws Exception {
        when(applicationConfig.getLocalConfigurationFilePath()).thenReturn(configFile.getAbsolutePath());
        Mono<ResponseEntity<String>> responseEntityMono = testHelper.restClientV3().putForEntity("/configuration",
                testHelper.configAsString());
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, Objects::isNull);
        //put Valid Configuration With New Ric should Update Repository. So, will wait until the ric size is 2
        await().until(rics::size, equalTo(2));
        //test Get Configuration
        Mono<ResponseEntity<String>> responseGetConfigMono = testHelper.restClientV3().getForEntity("/configuration");
        testHelper.testSuccessResponse(responseGetConfigMono, HttpStatus.OK, responseBody -> responseBody.contains("config"));
    }

    @Test
    void testHealthCheck() {
        Mono<ResponseEntity<String>> responseHealthCheckMono = testHelper.restClientV3().getForEntity("/status");
        testHelper.testSuccessResponse(responseHealthCheckMono, HttpStatus.OK, responseBody -> responseBody.contains("status"));
    }
}
