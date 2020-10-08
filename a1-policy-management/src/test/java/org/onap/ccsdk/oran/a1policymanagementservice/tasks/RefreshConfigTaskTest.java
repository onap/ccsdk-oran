/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.tasks;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.WARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate.Type;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser.ConfigParserResult;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableConfigParserResult;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.api.CbsClient;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.model.EnvProperties;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.model.ImmutableEnvProperties;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RefreshConfigTaskTest {

    private static final boolean CONFIG_FILE_EXISTS = true;
    private static final boolean CONFIG_FILE_DOES_NOT_EXIST = false;

    private RefreshConfigTask refreshTaskUnderTest;

    @Spy
    ApplicationConfig appConfig;

    @Mock
    CbsClient cbsClient;

    private static final String RIC_1_NAME = "ric1";
    private static final RicConfig CORRECT_RIC_CONIFG = ImmutableRicConfig.builder() //
            .ricId(RIC_1_NAME) //
            .baseUrl("http://localhost:8080/") //
            .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))) //
            .controllerName("") //
            .build();

    private static EnvProperties properties() {
        return ImmutableEnvProperties.builder() //
                .consulHost("host") //
                .consulPort(123) //
                .cbsName("cbsName") //
                .appName("appName") //
                .build();
    }

    private RefreshConfigTask createTestObject(boolean configFileExists) {
        return createTestObject(configFileExists, new Rics(), new Policies(), true);
    }

    private RefreshConfigTask createTestObject(boolean configFileExists, Rics rics, Policies policies,
            boolean stubConfigFileExists) {
        doReturn("fileName").when(appConfig).getLocalConfigurationFilePath();
        doReturn(null).when(appConfig).getWebClientConfig();

        RefreshConfigTask obj = spy(new RefreshConfigTask(appConfig, rics, policies, new Services(), new PolicyTypes(),
                new A1ClientFactory(appConfig)));
        if (stubConfigFileExists) {
            doReturn(configFileExists).when(obj).fileExists(any());
        }
        return obj;
    }

    @Test
    void stop_thenTaskIsDisposed() throws Exception {
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_DOES_NOT_EXIST, null, null, false);
        refreshTaskUnderTest.systemEnvironment = new Properties();

        refreshTaskUnderTest.start();
        refreshTaskUnderTest.stop();

        assertThat(refreshTaskUnderTest.getRefreshTask().isDisposed()).as("Refresh task is disposed").isTrue();
    }

    @Test
    void whenTheConfigurationFits_thenConfiguredRicsArePutInRepository() throws Exception {
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_EXISTS);
        refreshTaskUnderTest.systemEnvironment = new Properties();
        // When
        doReturn(getCorrectJson()).when(refreshTaskUnderTest).createInputStream(any());

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNext(Type.ADDED) //
                .expectNext(Type.ADDED) //
                .thenCancel() //
                .verify();

        // Then
        verify(refreshTaskUnderTest).loadConfigurationFromFile();

        verify(refreshTaskUnderTest, times(2)).runRicSynchronization(any(Ric.class));

        Iterable<RicConfig> ricConfigs = appConfig.getRicConfigs();
        RicConfig ricConfig = ricConfigs.iterator().next();
        assertThat(ricConfigs).isNotNull();
        assertThat(ricConfig).isEqualTo(CORRECT_RIC_CONIFG);
    }

    @Test
    void whenFileExistsButJsonIsIncorrect_thenNoRicsArePutInRepositoryAndErrorIsLogged() throws Exception {
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_EXISTS);
        refreshTaskUnderTest.systemEnvironment = new Properties();

        // When
        final String JUNK_JSON = "{\"junk }";
        doReturn(getJsonSteam(JUNK_JSON)).when(refreshTaskUnderTest).createInputStream(any());

        final ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(RefreshConfigTask.class, ERROR);

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNoEvent(Duration.ofMillis(100)) //
                .thenCancel() //
                .verify();

        // Then
        verify(refreshTaskUnderTest).loadConfigurationFromFile();
        assertThat(appConfig.getRicConfigs()).isEmpty();

        await().until(() -> logAppender.list.size() > 0);
        assertThat(logAppender.list.get(0).getFormattedMessage())
                .startsWith("Local configuration file not loaded: fileName, ");
    }

    @Test
    void whenPeriodicConfigRefreshNoConsul_thenErrorIsLogged() {
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_DOES_NOT_EXIST);
        refreshTaskUnderTest.systemEnvironment = new Properties();

        EnvProperties props = properties();
        doReturn(Mono.just(props)).when(refreshTaskUnderTest).getEnvironment(any());

        doReturn(Mono.just(cbsClient)).when(refreshTaskUnderTest).createCbsClient(props);
        when(cbsClient.get(any())).thenReturn(Mono.error(new IOException()));

        final ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(RefreshConfigTask.class, WARN);

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNoEvent(Duration.ofMillis(1000)) //
                .thenCancel() //
                .verify();

        await().until(() -> logAppender.list.size() > 0);
        assertThat(logAppender.list.get(0).getFormattedMessage())
                .isEqualTo("Could not refresh application configuration. java.io.IOException");
    }

    @Test
    void whenPeriodicConfigRefreshSuccess_thenNewConfigIsCreatedAndRepositoryUpdated() throws Exception {
        Rics rics = new Rics();
        Policies policies = new Policies();
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_DOES_NOT_EXIST, rics, policies, false);
        refreshTaskUnderTest.systemEnvironment = new Properties();

        RicConfig changedRicConfig = getRicConfig(RIC_1_NAME);
        rics.put(new Ric(changedRicConfig));
        RicConfig removedRicConfig = getRicConfig("removed");
        Ric removedRic = new Ric(removedRicConfig);
        rics.put(removedRic);
        appConfig.setConfiguration(configParserResult(changedRicConfig, removedRicConfig));

        Policy policy = getPolicy(removedRic);
        policies.put(policy);

        EnvProperties props = properties();
        doReturn(Mono.just(props)).when(refreshTaskUnderTest).getEnvironment(any());
        doReturn(Mono.just(cbsClient)).when(refreshTaskUnderTest).createCbsClient(props);

        JsonObject configAsJson = getJsonRootObject(getCorrectJson());
        String newBaseUrl = "newBaseUrl";
        modifyTheRicConfiguration(configAsJson, newBaseUrl);
        when(cbsClient.get(any())).thenReturn(Mono.just(configAsJson));
        doNothing().when(refreshTaskUnderTest).runRicSynchronization(any(Ric.class));

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNext(Type.CHANGED) //
                .expectNext(Type.ADDED) //
                .expectNext(Type.REMOVED) //
                .thenCancel() //
                .verify();

        assertThat(appConfig.getRicConfigs()).hasSize(2);
        assertThat(appConfig.getRic(RIC_1_NAME).baseUrl()).isEqualTo(newBaseUrl);
        String ric2Name = "ric2";
        assertThat(appConfig.getRic(ric2Name)).isNotNull();

        assertThat(rics.size()).isEqualTo(2);
        assertThat(rics.get(RIC_1_NAME).getConfig().baseUrl()).isEqualTo(newBaseUrl);
        assertThat(rics.get(ric2Name)).isNotNull();

        assertThat(policies.size()).isZero();
    }

    @Test
    void whenPeriodicConfigRefreshInvalidJson_thenErrorIsLogged() throws Exception {
        Rics rics = new Rics();
        Policies policies = new Policies();
        refreshTaskUnderTest = this.createTestObject(CONFIG_FILE_DOES_NOT_EXIST, rics, policies, false);
        refreshTaskUnderTest.systemEnvironment = new Properties();

        appConfig.setConfiguration(configParserResult());

        EnvProperties props = properties();
        doReturn(Mono.just(props)).when(refreshTaskUnderTest).getEnvironment(any());
        doReturn(Mono.just(cbsClient)).when(refreshTaskUnderTest).createCbsClient(props);

        JsonObject emptyJsonObject = new JsonObject();
        when(cbsClient.get(any())).thenReturn(Mono.just(emptyJsonObject));

        final ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(RefreshConfigTask.class, ERROR);

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNoEvent(Duration.ofMillis(1000)) //
                .thenCancel() //
                .verify();

        await().until(() -> logAppender.list.size() > 0);
        assertThat(logAppender.list.get(0).getFormattedMessage()).startsWith(
                "Could not parse configuration org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException: ");
    }

    private RicConfig getRicConfig(String name) {
        RicConfig ricConfig = ImmutableRicConfig.builder() //
                .ricId(name) //
                .baseUrl("url") //
                .managedElementIds(Collections.emptyList()) //
                .controllerName("controllerName") //
                .build();
        return ricConfig;
    }

    private Policy getPolicy(Ric ric) {
        ImmutablePolicyType type = ImmutablePolicyType.builder() //
                .id("type") //
                .schema("{}") //
                .build();
        Policy policy = ImmutablePolicy.builder() //
                .id("id") //
                .type(type) //
                .lastModified(Instant.now()) //
                .ric(ric) //
                .json("{}") //
                .ownerServiceId("ownerServiceId") //
                .isTransient(false) //
                .build();
        return policy;
    }

    ConfigParserResult configParserResult(RicConfig... rics) {
        return ImmutableConfigParserResult.builder() //
                .ricConfigs(Arrays.asList(rics)) //
                .dmaapConsumerTopicUrl("") //
                .dmaapProducerTopicUrl("") //
                .controllerConfigs(new HashMap<>()) //
                .build();
    }

    private void modifyTheRicConfiguration(JsonObject configAsJson, String newBaseUrl) {
        ((JsonObject) configAsJson.getAsJsonObject("config") //
                .getAsJsonArray("ric").get(0)) //
                        .addProperty("baseUrl", newBaseUrl);
    }

    private JsonObject getJsonRootObject(InputStream inStream)
            throws JsonIOException, JsonSyntaxException, IOException {
        JsonObject rootObject = JsonParser.parseReader(new InputStreamReader(inStream)).getAsJsonObject();
        return rootObject;
    }

    private static InputStream getCorrectJson() throws IOException {
        URL url = ApplicationConfigParser.class.getClassLoader().getResource("test_application_configuration.json");
        String string = Resources.toString(url, Charsets.UTF_8);
        return new ByteArrayInputStream((string.getBytes(StandardCharsets.UTF_8)));
    }

    private static InputStream getJsonSteam(String json) {
        return new ByteArrayInputStream((json.getBytes(StandardCharsets.UTF_8)));
    }
}
