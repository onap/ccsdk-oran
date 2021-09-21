/*-
 * ========================LICENSE_START=================================
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableWebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.vardata-directory=./target"})
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
    private static void setup() throws Exception {
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
    }

    @LocalServerPort
    private int port;

    @Test
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
    void getNoFileExists() {
        String url = "a1-policy/v2/configuration";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "File does not exist");
    }

    private String configAsString() throws Exception {
        File configFile = new File(getClass().getClassLoader()
                .getResource("test_application_configuration_with_dmaap_config.json").getFile());
        return FileUtils.readFileToString(configFile, "UTF-8");
    }

    @Test
    void putInvalidConfiguration_shouldReturnError400() throws Exception {
        String url = "a1-policy/v2/configuration";

        // Valid JSON but invalid configuration.
        testErrorCode(restClient().put(url, "{\"error\":\"error\"}"), HttpStatus.BAD_REQUEST,
                "Json schema validation failure");
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
        config = ImmutableWebClientConfig.builder() //
                .keyStoreType(config.keyStoreType()) //
                .keyStorePassword(config.keyStorePassword()) //
                .keyStore(config.keyStore()) //
                .keyPassword(config.keyPassword()) //
                .isTrustStoreUsed(false) //
                .trustStore(config.trustStore()) //
                .trustStorePassword(config.trustStorePassword()) //
                .httpProxyConfig(config.httpProxyConfig()) //
                .build();

        AsyncRestClientFactory f = new AsyncRestClientFactory(config);
        return f.createRestClientNoHttpProxy("https://localhost:" + port);

    }
}
