/*-
 * ========================LICENSE_START=================================
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.time.Duration;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
    "server.ssl.key-store=./config/keystore.jks", //
    "app.webclient.trust-store=./config/truststore.jks", //
    "app.vardata-directory=./target", //
    "app.config-file-schema-path=/application_configuration_schema.json" //
})
class ConfigurationControllerTest {
    @Autowired
    ApplicationContext context;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    private Rics rics;

    @TempDir
    public static File temporaryFolder;
    private static File configFile;

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

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("put Valid Configuration With New Ric should Update Repository")
    void putValidConfigurationWithNewRic_shouldUpdateRepository() throws Exception {
        String url = "a1-policy/v2/configuration";

        String resp = restClient().put(url, configAsString()).block();

        assertThat(resp).isEmpty();
        await().until(rics::size, equalTo(2));

        // GET config
        resp = restClient().get(url).block();
        assertThat(resp).contains("config");
    }

    @Test
    @DisplayName("get No File Exists")
    void getNoFileExists() {
        String url = "a1-policy/v2/configuration";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "File does not exist");
    }

    private String configAsString() throws Exception {
        File configFile =
                new File(getClass().getClassLoader().getResource("test_application_configuration.json").getFile());
        return FileUtils.readFileToString(configFile, "UTF-8");
    }

    @Test
    @DisplayName("put Invalid Configuration should Return Error 400")
    void putInvalidConfiguration_shouldReturnError400() throws Exception {
        String url = "a1-policy/v2/configuration";

        // Valid JSON but invalid configuration.
        testErrorCode(restClient().put(url, "{\"error\":\"error\"}"), HttpStatus.BAD_REQUEST, "");
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        StepVerifier.create(request) //
                .expectSubscription() //
                .expectErrorMatches(t -> checkWebClientError(t, expStatus, responseContains)) //
                .verify();
    }

    private boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);
        assertThat(responseException.getResponseBodyAsString()).contains(responseContains);
        assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        return true;
    }

    private AsyncRestClient restClient() {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        config = WebClientConfig.builder() //
                .keyStoreType(config.getKeyStoreType()) //
                .keyStorePassword(config.getKeyStorePassword()) //
                .keyStore(config.getKeyStore()) //
                .keyPassword(config.getKeyPassword()) //
                .isTrustStoreUsed(false) //
                .trustStore(config.getTrustStore()) //
                .trustStorePassword(config.getTrustStorePassword()) //
                .httpProxyConfig(config.getHttpProxyConfig()) //
                .build();

        AsyncRestClientFactory f = new AsyncRestClientFactory(config, new SecurityContext(""));
        return f.createRestClientNoHttpProxy("https://localhost:" + port);

    }
}
