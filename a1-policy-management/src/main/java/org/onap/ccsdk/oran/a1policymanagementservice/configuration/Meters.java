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

import io.micrometer.core.instrument.MeterRegistry;

import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.springframework.stereotype.Component;

/**
 * The aim is to collect statistical values from the A1 Policy Management
 * Service.
 * The counters are being updated every minute.
 */
@Component
public class Meters {

    public Meters(Rics rics, PolicyTypes policyTypes, Policies policies, MeterRegistry meterRegistry) {
        meterRegistry.gauge("total_ric_count", rics, Rics::size);
        meterRegistry.gauge("total_policy_type_count", policyTypes, PolicyTypes::size);
        meterRegistry.gauge("total_policy_count", policies, Policies::size);
    }
}
