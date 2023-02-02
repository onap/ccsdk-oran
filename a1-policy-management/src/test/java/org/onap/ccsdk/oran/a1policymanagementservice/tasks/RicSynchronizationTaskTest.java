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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
        ric1 = new Ric(RicConfig.builder() //
                .ricId(RIC_1_NAME) //
                .baseUrl("baseUrl1") //
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
    @DisplayName("test ric Idle And Error Deleting Policies then Synchronization With Failed Recovery")
    void ricIdleAndErrorDeletingPoliciesAllTheTime_thenSynchronizationWithFailedRecovery() {
        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();
        policies.put(policy1);
        WebClientResponseException exception = new WebClientResponseException(404, "", null, null, null);
        when(a1ClientMock.deleteAllPolicies(anySet())).thenReturn(Flux.error(exception));
        ric1.setState(RicState.AVAILABLE);
        runSynch(ric1);
        await().untilAsserted(() -> RicState.UNAVAILABLE.equals(ric1.getState()));
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test Connection Error")
    void testConnectionError() {
        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();
        policies.put(policy1);
        WebClientRequestException exception = new WebClientRequestException(new ServiceException("x"), null, null,
                new org.springframework.http.HttpHeaders());
        when(a1ClientMock.deleteAllPolicies(anySet())).thenReturn(Flux.error(exception));
        ric1.setState(RicState.AVAILABLE);
        runSynch(ric1);
        await().untilAsserted(() -> RicState.UNAVAILABLE.equals(ric1.getState()));
    }

    @Test
    @DisplayName("test ric Idle then Synchronization With Reuse Of Type From Repo And Correct Service Notified")
    void ricIdlePolicyTypeInRepo_thenSynchronizationWithReuseOfTypeFromRepoAndCorrectServiceNotified() {
        rics.put(ric1);
        ric1.setState(RicState.AVAILABLE);

        policyTypes.put(POLICY_TYPE_1);

        services.put(service1);
        Service serviceWithoutCallbackUrlShouldNotBeNotified = new Service("service2", Duration.ofSeconds(1), "");
        services.put(serviceWithoutCallbackUrlShouldNotBeNotified);

        setUpCreationOfA1Client();
        simulateRicWithOnePolicyType();

        ric1.setState(RicState.UNAVAILABLE);
        runSynch(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock, times(1)).getPolicyTypeIdentities();
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isEqualTo(1);
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    @DisplayName("test ric Idle then Synchronization With Type From Ric")
    void ricIdlePolicyTypeNotInRepo_thenSynchronizationWithTypeFromRic() throws Exception {
        ric1.setState(RicState.AVAILABLE);
        rics.put(ric1);

        setUpCreationOfA1Client();
        simulateRicWithOnePolicyType();
        String typeSchema = "schema";
        when(a1ClientMock.getPolicyTypeSchema(POLICY_TYPE_1_NAME)).thenReturn(Mono.just(typeSchema));

        ric1.setState(RicState.UNAVAILABLE);
        runSynch(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock).getPolicyTypeIdentities();
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isEqualTo(1);
        assertThat(policyTypes.getType(POLICY_TYPE_1_NAME).getSchema()).isEqualTo(typeSchema);
        assertThat(policies.size()).isZero();
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    @DisplayName("test ric Idle then Synchronization With Recreation Of Policies")
    void ricIdleAndHavePolicies_thenSynchronizationWithRecreationOfPolicies() {
        ric1.setState(RicState.AVAILABLE);
        rics.put(ric1);

        Policy transientPolicy = createPolicy("transientPolicyId", true);

        policies.put(transientPolicy);
        policies.put(policy1);

        setUpCreationOfA1Client();
        simulateRicWithNoPolicyTypes();

        when(a1ClientMock.deleteAllPolicies(anySet())).thenReturn(Flux.just("OK"));
        when(a1ClientMock.putPolicy(any(Policy.class))).thenReturn(Mono.just("OK"));

        ric1.setState(RicState.UNAVAILABLE);
        runSynch(ric1);
        await().untilAsserted(() -> RicState.AVAILABLE.equals(ric1.getState()));

        verify(a1ClientMock).deleteAllPolicies(anySet());
        verify(a1ClientMock).putPolicy(policy1);
        verifyNoMoreInteractions(a1ClientMock);

        assertThat(policyTypes.size()).isZero();
        assertThat(policies.size()).isEqualTo(1); // The transient policy shall be deleted
        assertThat(ric1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    private void runSynch(Ric ric) {
        RicSynchronizationTask synchronizerUnderTest = createTask();
        ric.getLock().lock(LockType.EXCLUSIVE, "RicSynchronizationTask") //
                .flatMap(notUsed -> synchronizerUnderTest.synchronizeRic(ric)) //
                .doFinally(sig -> ric.getLock().unlockBlocking()) //
                .block();
    }

    private void setUpCreationOfA1Client() {
        when(a1ClientFactoryMock.createA1Client(any(Ric.class))).thenReturn(Mono.just(a1ClientMock));
        doReturn(Flux.empty()).when(a1ClientMock).deleteAllPolicies(anySet());
    }

    private void simulateRicWithOnePolicyType() {
        when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.just(Arrays.asList(POLICY_TYPE_1_NAME)));
    }

    private void simulateRicWithNoPolicyTypes() {
        when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.just(Collections.emptyList()));
    }
}
