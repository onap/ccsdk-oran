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

import static ch.qos.logback.classic.Level.WARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.awaitility.Durations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;

import reactor.core.publisher.Mono;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class ServiceSupervisionTest {

    private static final String SERVICE_NAME = "Service name";
    private static final String RIC_NAME = "name";
    private static final String POLICY_ID = "policy";

    @Mock
    A1ClientFactory a1ClientFactoryMock;
    @Mock
    A1Client a1ClientMock;

    private Services services;
    private Service service;
    private Policies policies;
    private RicConfig ricConfig = RicConfig.builder() //
            .ricId(RIC_NAME) //
            .baseUrl("baseUrl") //
            .build();
    private Ric ric = new Ric(ricConfig);
    private PolicyType policyType = PolicyType.builder() //
            .id("policyTypeName") //
            .schema("schema") //
            .build();
    private Policy policy = Policy.builder() //
            .id(POLICY_ID) //
            .json("json") //
            .ownerServiceId(SERVICE_NAME) //
            .ric(ric) //
            .type(policyType) //
            .lastModified(Instant.now()) //
            .isTransient(false) //
            .statusNotificationUri("statusNotificationUri") //
            .build();

    @Test
    @DisplayName("test service Expired policy And Service Are Deleted In Repo And Policy Is Deleted In Ric")
    void serviceExpired_policyAndServiceAreDeletedInRepoAndPolicyIsDeletedInRic() {
        setUpRepositoryWithKeepAliveInterval(Duration.ofSeconds(2));

        setUpCreationOfA1Client();
        when(a1ClientMock.deletePolicy(any(Policy.class))).thenReturn(Mono.just("Policy deleted"));

        ServiceSupervision serviceSupervisionUnderTest =
                new ServiceSupervision(services, policies, a1ClientFactoryMock);

        await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_SECOND).until(service::isExpired);

        serviceSupervisionUnderTest.checkAllServices().blockLast();

        assertThat(policies.size()).isZero();
        assertThat(services.size()).isZero();

        verify(a1ClientMock).deletePolicy(policy);
        verifyNoMoreInteractions(a1ClientMock);
    }

    @Test
    @DisplayName("test service Expired But Delete In Ric Fails policy And Service Are Deleted In Repo And Error Logged For Ric")
    void serviceExpiredButDeleteInRicFails_policyAndServiceAreDeletedInRepoAndErrorLoggedForRic() {
        setUpRepositoryWithKeepAliveInterval(Duration.ofSeconds(2));

        setUpCreationOfA1Client();
        String originalErrorMessage = "Failed";
        when(a1ClientMock.deletePolicy(any(Policy.class))).thenReturn(Mono.error(new Exception(originalErrorMessage)));

        ServiceSupervision serviceSupervisionUnderTest =
                new ServiceSupervision(services, policies, a1ClientFactoryMock);

        await().atMost(Durations.FIVE_SECONDS).with().pollInterval(Durations.ONE_SECOND).until(service::isExpired);

        final ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(ServiceSupervision.class, WARN);

        serviceSupervisionUnderTest.checkAllServices().blockLast();

        assertThat(policies.size()).isZero();
        assertThat(services.size()).isZero();

        ILoggingEvent loggingEvent = logAppender.list.get(0);
        assertThat(loggingEvent.getLevel()).isEqualTo(WARN);
        String expectedLogMessage =
                "Could not delete policy: " + POLICY_ID + " from ric: " + RIC_NAME + ". Cause: " + originalErrorMessage;
        assertThat(loggingEvent.getFormattedMessage()).isEqualTo(expectedLogMessage);
    }

    @Test
    @DisplayName("test service Not Expired should Not Be Checked")
    void serviceNotExpired_shouldNotBeChecked() {
        setUpRepositoryWithKeepAliveInterval(Duration.ofSeconds(2));

        ServiceSupervision serviceSupervisionUnderTest =
                new ServiceSupervision(services, policies, a1ClientFactoryMock);

        serviceSupervisionUnderTest.checkAllServices().blockLast();

        assertThat(policies.size()).isEqualTo(1);
        assertThat(services.size()).isEqualTo(1);

        verifyNoInteractions(a1ClientFactoryMock);
        verifyNoInteractions(a1ClientMock);
    }

    @Test
    @DisplayName("test service Without Keep Alive Interval should Not Be Checked")
    void serviceWithoutKeepAliveInterval_shouldNotBeChecked() {
        setUpRepositoryWithKeepAliveInterval(Duration.ofSeconds(0));

        ServiceSupervision serviceSupervisionUnderTest =
                new ServiceSupervision(services, policies, a1ClientFactoryMock);

        serviceSupervisionUnderTest.checkAllServices().blockLast();

        assertThat(policies.size()).isEqualTo(1);
        assertThat(services.size()).isEqualTo(1);

        verifyNoInteractions(a1ClientFactoryMock);
        verifyNoInteractions(a1ClientMock);
    }

    private void setUpCreationOfA1Client() {
        when(a1ClientFactoryMock.createA1Client(any(Ric.class))).thenReturn(Mono.just(a1ClientMock));
    }

    private void setUpRepositoryWithKeepAliveInterval(Duration keepAliveInterval) {
        ApplicationConfig appConfig = new ApplicationConfig();
        services = new Services(appConfig);
        service = new Service(SERVICE_NAME, keepAliveInterval, "callbackUrl");
        services.put(service);
        policies = new Policies(appConfig);
        policies.put(policy);
    }
}
