/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2022 Nordix Foundation. All rights reserved.
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
import static org.mockito.Mockito.spy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.time.Instant;
import java.util.Arrays;
import java.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;

@ExtendWith(MockitoExtension.class)
class RefreshCounterTaskTest {

    private static final String POLICY_TYPE_1_NAME = "type1";
    private static final PolicyType POLICY_TYPE_1 = PolicyType.builder().id(POLICY_TYPE_1_NAME).schema("").build();

    private static final Ric RIC_1 = new Ric(RicConfig.builder().ricId("ric_1").baseUrl("baseUrl1")
            .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))).controllerName("controllerName")
            .build());

    private static final String POLICY_1_ID = "policyId1";
    private static final Policy POLICY_1 = Policy.builder().id(POLICY_1_ID).json("").ownerServiceId("service")
            .ric(RIC_1).type(POLICY_TYPE_1).lastModified(Instant.now()).isTransient(false)
            .statusNotificationUri("statusNotificationUri").build();

    private final PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final ApplicationConfig appConfig = new ApplicationConfig();

    private PolicyTypes policyTypes;
    private Policies policies;
    private Rics rics = new Rics();

    @BeforeEach
    void init() {
        policyTypes = new PolicyTypes(appConfig);
        policies = new Policies(appConfig);

        rics.clear();
        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.clearSupportedPolicyTypes();
    }

    @Test
    void testCounters_whenNeitherChangedPoliciesNorPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        policyTypes.put(POLICY_TYPE_1);

        policies.put(POLICY_1);

        RefreshCounterTask spy = spy(createRefreshCounterTask()); // instantiate RefreshCounterTask
        MeterRegistry meterRegistry = spy.getMeterRegistry();

        assertThat(meterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("total_policy_count").gauge().value()).isEqualTo(1);
    }

    @Test
    void testCounters_whenChangedPoliciesAndNoChangedPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        policyTypes.put(POLICY_TYPE_1);

        policies.put(POLICY_1);

        String POLICY_2_ID = "policyId2";
        Policy POLICY_2 = Policy.builder()
                .id(POLICY_2_ID)
                .json("")
                .ownerServiceId("service")
                .ric(RIC_1)
                .type(POLICY_TYPE_1)
                .lastModified(Instant.now())
                .isTransient(false) //
                .statusNotificationUri("statusNotificationUri")
                .build();

        policies.put(POLICY_2);

        RefreshCounterTask spy = spy(createRefreshCounterTask()); // instantiate RefreshCounterTask
        MeterRegistry meterRegistry = spy.getMeterRegistry();

        assertThat(meterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("total_policy_count").gauge().value()).isEqualTo(2);
    }

    @Test
    void testCounters_whenNoChangedPoliciesAndChangedPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);

        String POLICY_TYPE_2_NAME = "type2";
        PolicyType POLICY_TYPE_2 = PolicyType.builder()
                .id(POLICY_TYPE_2_NAME)
                .schema("")
                .build();

        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_2);
        rics.put(RIC_1);

        policyTypes.put(POLICY_TYPE_1);
        policyTypes.put(POLICY_TYPE_2);

        policies.put(POLICY_1);

        RefreshCounterTask spy = spy(createRefreshCounterTask()); // instantiate RefreshCounterTask
        MeterRegistry meterRegistry = spy.getMeterRegistry();

        assertThat(meterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(meterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(2);
        assertThat(meterRegistry.get("total_policy_count").gauge().value()).isEqualTo(1);
    }

    private RefreshCounterTask createRefreshCounterTask() {
        return new RefreshCounterTask(rics, policyTypes, policies, prometheusMeterRegistry);
    }
}
