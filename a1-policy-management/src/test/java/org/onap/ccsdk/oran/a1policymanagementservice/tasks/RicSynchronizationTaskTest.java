/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
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

package org.onap.ccsdk.oran.a1policymanagementservice.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RicSynchronizationTaskTest {
    private static final String POLICY_TYPE_1_NAME = "type1";
    private static final PolicyType POLICY_TYPE_1 = PolicyType.builder() //
            .id(POLICY_TYPE_1_NAME) //
            .schema("") //
            .build();

    private static final String RIC_1_NAME = "ric1";

    private static Ric ric1;

    private static Policy createPolicy(String policyId, boolean isTransient) {
        return Policy.builder() //
                .id(policyId) //
                .json("") //
                .ownerServiceId("service") //
                .ric(ric1) //
                .type(POLICY_TYPE_1) //
                .lastModified(Instant.now()) //
                .isTransient(isTransient) //
                .statusNotificationUri("statusNotificationUri") //
                .build();
    }

    private static Policy policy1;
    private static final String SERVICE_1_NAME = "service1";
    private static final String SERVICE_1_CALLBACK_URL = "callbackUrl";
    private static Service service1;

    @Mock
    private A1Client a1ClientMock;

    @Mock
    private A1ClientFactory a1ClientFactoryMock;

    private PolicyTypes policyTypes;
    private Policies policies;
    private Services services;
    private Rics rics;

    private final ApplicationConfig appConfig = new ApplicationConfig();

    @BeforeEach
    void init() {
        ric1 = new Ric(ImmutableRicConfig.builder() //
                .ricId(RIC_1_NAME) //
                .baseUrl("baseUrl1") //
                .managedElementIds(Collections.emptyList()) //
                .controllerName("controllerName") //
                .build());
        policy1 = createPolicy("policyId1", false);
        policyTypes = new PolicyTypes(appConfig);
        policies = new Policies(appConfig);
        services = new Services(appConfig);
        rics = new Rics();

        service1 = new Service(SERVICE_1_NAME, Duration.ofSeconds(1), SERVICE_1_CALLBACK_URL);
    }

    private RicSynchronizationTask createTask() {
        ApplicationConfig config = new ApplicationConfig();
        AsyncRestClientFactory restClientFactory =
                new AsyncRestClientFactory(config.getWebClientConfig(), new SecurityContext(""));
        return new RicSynchronizationTask(a1ClientFactoryMock, policyTypes, policies, services, restClientFactory,
                rics);
    };

    @Test
    void ricAlreadySynchronizing_thenNoSynchronization() {
        ric1.setState(RicState.SYNCHRONIZING);
        ric1.addSupportedPolicyType(POLICY_TYPE_1);

        policyTypes.put(POLICY_TYPE_1);
        policies.put(policy1);

        RicSynchronizationTask synchronizerUnderTest = createTask();

        synchronizerUnderTest.run(ric1);

        verifyNoInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isEqualTo(1);
        assertThat(policies.size()).isEqualTo(1);
        assertThat(ric1.getState()).isEqualTo(RicState.SYNCHRONIZING);
        assertThat(ric1.getSupportedPolicyTypeNames()).hasSize(1);
    }

    @Test
    void ricIdlePolicyTypeInRepo_thenSynchronizationWithReuseOfTypeFromRepoAndCorrectServiceNotified() {
        rics.put(ric1);
        ric1.setState(RicState.AVAILABLE);

        policyTypes.put(POLICY_TYPE_1);

        services.put(service1);
        Service serviceWithoutCallbackUrlShouldNotBeNotified = new Service("service2", Duration.ofSeconds(1), "");
        services.put(serviceWithoutCallbackUrlShouldNotBeNotified);

        setUpCreationOfA1Client();
        simulateRicWithOnePolicyType();

        RicSynchronizationTask synchronizerUnderTest = spy(createTask());

        ric1.setState(RicState.UNAVAILABLE);
        synchronizerUnderTest.run(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock, times(1)).getPolicyTypeIdentities();
        verifyNoMoreInteractions(a1ClientMock);

        verify(synchronizerUnderTest).run(ric1);

        assertThat(policyTypes.size()).isEqualTo(1);
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    void ricIdlePolicyTypeNotInRepo_thenSynchronizationWithTypeFromRic() throws Exception {
        ric1.setState(RicState.AVAILABLE);
        rics.put(ric1);

        setUpCreationOfA1Client();
        simulateRicWithOnePolicyType();
        String typeSchema = "schema";
        when(a1ClientMock.getPolicyTypeSchema(POLICY_TYPE_1_NAME)).thenReturn(Mono.just(typeSchema));

        RicSynchronizationTask synchronizerUnderTest = createTask();

        ric1.setState(RicState.UNAVAILABLE);
        synchronizerUnderTest.run(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock).getPolicyTypeIdentities();
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isEqualTo(1);
        assertThat(policyTypes.getType(POLICY_TYPE_1_NAME).getSchema()).isEqualTo(typeSchema);
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    void ricIdleAndHavePolicies_thenSynchronizationWithRecreationOfPolicies() {
        ric1.setState(RicState.AVAILABLE);
        rics.put(ric1);

        Policy transientPolicy = createPolicy("transientPolicyId", true);

        policies.put(transientPolicy);
        policies.put(policy1);

        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();

        when(a1ClientMock.deleteAllPolicies()).thenReturn(Flux.just("OK"));
        when(a1ClientMock.putPolicy(any(Policy.class))).thenReturn(Mono.just("OK"));

        RicSynchronizationTask synchronizerUnderTest = createTask();

        ric1.setState(RicState.UNAVAILABLE);
        synchronizerUnderTest.run(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock).deleteAllPolicies();
        verify(a1ClientMock).putPolicy(policy1);
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isZero();
        assertThat(policies.size()).isEqualTo(1); // The transient policy shall be deleted
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    void ricIdleAndErrorDeletingPoliciesFirstTime_thenSynchronizationWithDeletionOfPolicies() {
        ric1.setState(RicState.AVAILABLE);
        rics.put(ric1);

        policies.put(policy1);

        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();

        when(a1ClientMock.deleteAllPolicies()) //
                .thenReturn(Flux.error(new Exception("Exception"))) //
                .thenReturn(Flux.just("OK"));

        RicSynchronizationTask synchronizerUnderTest = createTask();

        ric1.setState(RicState.UNAVAILABLE);
        synchronizerUnderTest.run(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock, times(2)).deleteAllPolicies();
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isZero();
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    void ricIdleAndErrorDeletingPoliciesAllTheTime_thenSynchronizationWithFailedRecovery() {
        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();

        policies.put(policy1);

        String originalErrorMessage = "Exception";
        when(a1ClientMock.deleteAllPolicies()).thenReturn(Flux.error(new Exception(originalErrorMessage)));

        RicSynchronizationTask synchronizerUnderTest = createTask();

        ric1.setState(RicState.AVAILABLE);
        synchronizerUnderTest.run(ric1);
        await().untilAsserted(() -> RicState.UNAVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock, times(2)).deleteAllPolicies();
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isZero();
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    private void setUpCreationOfA1Client() {
        when(a1ClientFactoryMock.createA1Client(any(Ric.class))).thenReturn(Mono.just(a1ClientMock));
        doReturn(Flux.empty()).when(a1ClientMock).deleteAllPolicies();
    }

    private void simulateRicWithOnePolicyType() {
        when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.just(Arrays.asList(POLICY_TYPE_1_NAME)));
    }

    private void simulateRicWithNoPolicyTypes() {
        when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.just(Collections.emptyList()));
    }
}
