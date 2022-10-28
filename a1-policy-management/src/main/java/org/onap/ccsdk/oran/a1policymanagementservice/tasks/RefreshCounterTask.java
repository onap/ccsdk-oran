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

import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.MethodHandles;
import lombok.AccessLevel;
import lombok.Getter;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The aim is to collect statistical values from the A1 Policy Management Service.
 */
@Component
public class RefreshCounterTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private final Rics rics;

    @Autowired
    private final PolicyTypes policyTypes;

    @Autowired
    private final Policies policies;

    @Autowired
    @Getter(AccessLevel.PUBLIC)
    private final MeterRegistry meterRegistry;

    @Autowired
    public RefreshCounterTask(Rics rics, PolicyTypes policyTypes, Policies policies, MeterRegistry meterRegistry) {
        this.rics = rics;
        this.policyTypes = policyTypes;
        this.policies = policies;
        this.meterRegistry = meterRegistry;

        logger.trace("Counters have been initialized.");
        meterRegistry.gauge("total_ric_count", rics, Rics::size);
        meterRegistry.gauge("total_policy_type_count", policyTypes, PolicyTypes::size);
        meterRegistry.gauge("total_policy_count", policies, Policies::size);
    }

}
