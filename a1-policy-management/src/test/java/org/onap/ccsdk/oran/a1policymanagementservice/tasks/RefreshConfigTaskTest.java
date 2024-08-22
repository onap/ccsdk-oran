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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate.Type;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser.ConfigParserResult;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;

import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RefreshConfigTaskTest {

    private RefreshConfigTask refreshTaskUnderTest;

    @Spy
    ApplicationConfig appConfig;

    @Mock
    ConfigurationFile configurationFileMock;

    private RefreshConfigTask createTestObject() {
        return createTestObject(spy(new Rics()), new Policies(appConfig), true);
    }

    private RefreshConfigTask createTestObject(Rics rics, Policies policies,
            boolean stubConfigFileExists) {
        SecurityContext secContext = new SecurityContext("");

        RefreshConfigTask obj =
                spy(new RefreshConfigTask(configurationFileMock, appConfig, rics, policies, new Services(appConfig),
                        new PolicyTypes(appConfig), new A1ClientFactory(appConfig, secContext), secContext));
        if (stubConfigFileExists) {
            when(configurationFileMock.readFile()).thenReturn(Optional.empty());
            doReturn(123L).when(configurationFileMock).getLastModified();
        }
        return obj;
    }

    @Test
    @DisplayName("test when The Configuration Fits then Configured Rics Are Put In Repository")
    void whenTheConfigurationFits_thenConfiguredRicsArePutInRepository() throws Exception {
        refreshTaskUnderTest = this.createTestObject();
        refreshTaskUnderTest.systemEnvironment = new Properties();
        // When
        when(configurationFileMock.readFile()).thenReturn(getCorrectJson());

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNext(Type.ADDED) //
                .expectNext(Type.ADDED) //
                .thenCancel() //
                .verify();

        // Then
        verify(refreshTaskUnderTest, atLeastOnce()).loadConfigurationFromFile();

        verify(refreshTaskUnderTest.rics, times(2)).put(any(Ric.class));

        java.util.Collection<RicConfig> ricConfigs = appConfig.getRicConfigs();
        assertThat(ricConfigs).isNotNull().hasSize(2);
    }

    @Test
    @DisplayName("test when File Exists But Json Is Incorrect then No Rics Are Put In Repository")
    void whenFileExistsButJsonIsIncorrect_thenNoRicsArePutInRepository() {
        refreshTaskUnderTest = this.createTestObject();

        // When
        when(configurationFileMock.readFile()).thenReturn(Optional.empty());

        StepVerifier //
                .create(refreshTaskUnderTest.createRefreshTask()) //
                .expectSubscription() //
                .expectNoEvent(Duration.ofMillis(100)) //
                .thenCancel() //
                .verify();

        // Then
        verify(refreshTaskUnderTest).loadConfigurationFromFile();
        assertThat(appConfig.getRicConfigs()).isEmpty();
    }

    ConfigParserResult configParserResult(RicConfig... rics) {
        return ConfigParserResult.builder() //
                .ricConfigs(Arrays.asList(rics)) //
                .build();
    }

    private static Optional<JsonObject> getCorrectJson() throws IOException {
        URL url = ApplicationConfigParser.class.getClassLoader().getResource("test_application_configuration.json");
        String string = Resources.toString(url, Charsets.UTF_8);
        return Optional.of(JsonParser.parseString(string).getAsJsonObject());
    }
}
