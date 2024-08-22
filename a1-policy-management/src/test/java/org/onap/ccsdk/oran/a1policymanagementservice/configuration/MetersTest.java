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

package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.time.Instant;
import java.util.Arrays;
import java.util.Vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;

@ExtendWith(MockitoExtension.class)
class MetersTest {

    private static final String POLICY_TYPE_1_NAME = "type1";
    private static final PolicyType POLICY_TYPE_1 = PolicyType.builder().id(POLICY_TYPE_1_NAME).schema("").build();

    private static final Ric RIC_1 = new Ric(RicConfig.builder().ricId("ric_1").baseUrl("baseUrl1")
            .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))).build());

    private static final String POLICY_1_ID = "policyId1";
    private static final Policy POLICY_1 = Policy.builder().id(POLICY_1_ID).json("").ownerServiceId("service")
            .ric(RIC_1).type(POLICY_TYPE_1).lastModified(Instant.now()).isTransient(false)
            .statusNotificationUri("statusNotificationUri").build();

    private PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final ApplicationConfig appConfig = new ApplicationConfig();

    private PolicyTypes types;
    private Policies policies;
    private Rics rics = new Rics();

    Meters testObject;

    @BeforeEach
    void init() {
        types = new PolicyTypes(appConfig);
        policies = new Policies(appConfig);

        rics.clear();
        policies.clear();
        types.clear();

        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.clearSupportedPolicyTypes();

        this.testObject = createMeters();
    }

    @Test
    @DisplayName("test Counters when Neither Changed Policies Nor Policy Types")
    void testCounters_whenNeitherChangedPoliciesNorPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        types.put(POLICY_TYPE_1);

        policies.put(POLICY_1);

        createMeters();

        assertThat(prometheusMeterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(prometheusMeterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(1);
        assertThat(prometheusMeterRegistry.get("total_policy_count").gauge().value()).isEqualTo(1);
    }

    @Test
    @DisplayName("test Counters when Changed Policies And No Changed Policy Types")
    void testCounters_whenChangedPoliciesAndNoChangedPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        rics.put(RIC_1);

        types.put(POLICY_TYPE_1);

        policies.put(POLICY_1);

        String POLICY_2_ID = "policyId2";
        Policy POLICY_2 = Policy.builder() //
                .id(POLICY_2_ID) //
                .json("") //
                .ownerServiceId("service") //
                .ric(RIC_1) //
                .type(POLICY_TYPE_1) //
                .lastModified(Instant.now()) //
                .isTransient(false) //
                .statusNotificationUri("statusNotificationUri") //
                .build();

        policies.put(POLICY_2);

        assertThat(prometheusMeterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(prometheusMeterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(1);
        assertThat(prometheusMeterRegistry.get("total_policy_count").gauge().value()).isEqualTo(2);
    }

    @Test
    @DisplayName("test Counters when No Changed Policies And Changed Policy Types")
    void testCounters_whenNoChangedPoliciesAndChangedPolicyTypes() {
        RIC_1.setState(Ric.RicState.AVAILABLE);

        String POLICY_TYPE_2_NAME = "type2";
        PolicyType POLICY_TYPE_2 = PolicyType.builder() //
                .id(POLICY_TYPE_2_NAME) //
                .schema("") //
                .build();

        RIC_1.addSupportedPolicyType(POLICY_TYPE_1);
        RIC_1.addSupportedPolicyType(POLICY_TYPE_2);
        rics.put(RIC_1);

        types.put(POLICY_TYPE_1);
        types.put(POLICY_TYPE_2);

        policies.put(POLICY_1);

        assertThat(prometheusMeterRegistry.get("total_ric_count").gauge().value()).isEqualTo(1);
        assertThat(prometheusMeterRegistry.get("total_policy_type_count").gauge().value()).isEqualTo(2);
        assertThat(prometheusMeterRegistry.get("total_policy_count").gauge().value()).isEqualTo(1);
    }

    private Meters createMeters() {
        return new Meters(rics, types, policies, prometheusMeterRegistry);
    }
}
