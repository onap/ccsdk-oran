/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
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

package org.onap.ccsdk.oran.a1policymanagementservice.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RicSupervisionTest {
    private static final String POLICY_TYPE_1_NAME = "type1";
    private static final PolicyType POLICY_TYPE_1 = PolicyType.builder() //
            .id(POLICY_TYPE_1_NAME) //
            .schema("") //
            .build();

    private static final Ric RIC_1 = new Ric(RicConfig.builder() //
            .ricId("ric_1") //
            .baseUrl("baseUrl1") //
            .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))) //
            .build());

    private static final String POLICY_1_ID = "policyId1";
    private static final Policy POLICY_1 = Policy.builder() //
            .id(POLICY_1_ID) //
            .json("") //
            .ownerServiceId("service") //
            .ric(RIC_1) //
            .type(POLICY_TYPE_1) //
            .lastModified(Instant.now()) //
            .isTransient(false) //
            .statusNotificationUri("statusNotificationUri") //
            .build();

    private static final Policy POLICY_2 = Policy.builder() //
            .id("policyId2") //
            .json("") //
            .ownerServiceId("service") //
            .ric(RIC_1) //
            .type(POLICY_TYPE_1) //
            .lastModified(Instant.now()) //
            .isTransient(false) //
            .statusNotificationUri("statusNotificationUri") //
            .build();

    @Mock
    private A1Client a1ClientMock;

    @Mock
    private A1ClientFactory a1ClientFactory;

    @Mock
    private RicSynchronizationTask synchronizationTaskMock;

    private final ApplicationConfig appConfig = new ApplicationConfig();

    private PolicyTypes types;
    private Policies policies;
    private Rics rics = new Rics();

    @BeforeEach
    void init() {
        types = new PolicyTypes(appConfig);
        policies = new Policies(appConfig);

        rics.clear();
        RIC_1.setState(RicState.UNAVAILABLE);
        RIC_1.clearSupportedPolicyTypes();
    }

    @AfterEach
    void verifyNoRicLocks() {
        for (Ric ric : this.rics.getRics()) {
            Lock.Grant grant = ric.getLock().lockBlocking(LockType.EXCLUSIVE, "");
            grant.unlockBlocking();
            assertThat(ric.getLock().getLockCounter()).isZero();
        }
    }

    @Test
    @DisplayName("test when Ric Idle And No Changed Policies Or PolicyTypes then No Synchronization")
    void whenRicIdleAndNoChangedPoliciesOrPolicyTypes_thenNoSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        types.put(POLICY_TYPE_1);

        policies.put(POLICY_1);

        setUpGetPolicyIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_1_ID)));
        setUpGetPolicyTypeIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_TYPE_1_NAME)));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock, times(0)).synchronizeRic(RIC_1);
        assertThat(RIC_1.getState()).isEqualTo(RicState.AVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Undefined then Synchronization")
    void whenRicUndefined_thenSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.UNAVAILABLE);
        rics.put(RIC_1);
        RicSupervision supervisorUnderTest = spy(createRicSupervision());
        doReturn(synchronizationTaskMock).when(supervisorUnderTest).createSynchronizationTask();
        doReturn(Mono.just(RIC_1)).when(synchronizationTaskMock).synchronizeRic(any());
        supervisorUnderTest.checkAllRics();
        verify(synchronizationTaskMock).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Synchronizing then No Synchronization")
    void whenRicSynchronizing_thenNoSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.SYNCHRONIZING);
        rics.put(RIC_1);

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock, times(0)).synchronizeRic(RIC_1);
        assertThat(RIC_1.getState()).isEqualTo(RicState.SYNCHRONIZING);
    }

    @Test
    @DisplayName("test when Ric Idle And Error Getting Policy Identities then No Synchronization")
    void whenRicIdleAndErrorGettingPolicyIdentities_thenNoSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        setUpGetPolicyIdentitiesToReturn(new Exception("Failed"));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());
        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock, times(0)).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Idle And Not Same Amount Of Policies then Synchronization")
    void whenRicIdleAndNotSameAmountOfPolicies_thenSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        rics.put(RIC_1);

        policies.put(POLICY_1);
        policies.put(POLICY_2);

        setUpGetPolicyIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_1_ID)));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        doReturn(synchronizationTaskMock).when(supervisorUnderTest).createSynchronizationTask();

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Idle And Same Amount Of Policies But Not Same Policies then Synchronization")
    void whenRicIdleAndSameAmountOfPoliciesButNotSamePolicies_thenSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        rics.put(RIC_1);

        policies.put(POLICY_1);
        policies.put(POLICY_2);

        setUpGetPolicyIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_1_ID, "Another_policy")));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        doReturn(synchronizationTaskMock).when(supervisorUnderTest).createSynchronizationTask();

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Idle And Error Getting Policy Types then No Synchronization")
    void whenRicIdleAndErrorGettingPolicyTypes_thenNoSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        setUpGetPolicyIdentitiesToReturn(Collections.emptyList());
        setUpGetPolicyTypeIdentitiesToReturn(new Exception("Failed"));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());
        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock, times(0)).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Idle And Not Same Amount Of PolicyTypes then Synchronization")
    void whenRicIdleAndNotSameAmountOfPolicyTypes_thenSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        RIC_1.setState(RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        types.put(POLICY_TYPE_1);

        setUpGetPolicyIdentitiesToReturn(Collections.emptyList());
        setUpGetPolicyTypeIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_TYPE_1_NAME, "another_policy_type")));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        doReturn(synchronizationTaskMock).when(supervisorUnderTest).createSynchronizationTask();

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock).synchronizeRic(RIC_1);

        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @Test
    @DisplayName("test when Ric Idle And Same Amount Of Policy Types But Not Same Types then Synchronization")
    void whenRicIdleAndSameAmountOfPolicyTypesButNotSameTypes_thenSynchronization() {
        doReturn(Mono.just(a1ClientMock)).when(a1ClientFactory).createA1Client(any(Ric.class));
        PolicyType policyType2 = PolicyType.builder() //
                .id("policyType2") //
                .schema("") //
                .build();

        RIC_1.setState(RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        RIC_1.addSupportedPolicyType(policyType2);
        rics.put(RIC_1);

        setUpGetPolicyIdentitiesToReturn(Collections.emptyList());
        setUpGetPolicyTypeIdentitiesToReturn(new ArrayList<>(Arrays.asList(POLICY_TYPE_1_NAME, "another_policy_type")));

        RicSupervision supervisorUnderTest = spy(createRicSupervision());

        doReturn(synchronizationTaskMock).when(supervisorUnderTest).createSynchronizationTask();

        supervisorUnderTest.checkAllRics();

        verify(supervisorUnderTest).checkAllRics();
        verify(synchronizationTaskMock).synchronizeRic(RIC_1);
        assertThat(RIC_1.getState()).isEqualTo(RicState.UNAVAILABLE);
    }

    @SuppressWarnings("unchecked")
    private void setUpGetPolicyIdentitiesToReturn(Object returnValue) {
        if (returnValue instanceof List<?>) {
            when(a1ClientMock.getPolicyIdentities()).thenReturn(Mono.just((List<String>) returnValue));
        } else if (returnValue instanceof Exception) {
            when(a1ClientMock.getPolicyIdentities()).thenReturn(Mono.error((Exception) returnValue));
        }
    }

    @SuppressWarnings("unchecked")
    private void setUpGetPolicyTypeIdentitiesToReturn(Object returnValue) {
        if (returnValue instanceof List<?>) {
            when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.just((List<String>) returnValue));
        } else if (returnValue instanceof Exception) {
            when(a1ClientMock.getPolicyTypeIdentities()).thenReturn(Mono.error((Exception) returnValue));
        }
    }

    private RicSupervision createRicSupervision() {
        ApplicationConfig config = new ApplicationConfig();
        return new RicSupervision(rics, policies, a1ClientFactory, types, null, config, new SecurityContext(""));
    }
}
