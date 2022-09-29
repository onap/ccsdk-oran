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
import java.util.concurrent.atomic.AtomicLong;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The aim is to collect statistical values from the A1 Policy Management Service.
 * The counters are being updated every minute.
 */
@EnableScheduling
@Component
public class RefreshCounterTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Rics rics;
    private final PolicyTypes policyTypes;
    private final Policies policies;

    private final MeterRegistry meterRegistry;

    private final AtomicLong ricCount;
    private final AtomicLong policyTypeCount;
    private final AtomicLong policyCount;

    @Autowired
    public RefreshCounterTask(Rics rics, PolicyTypes policyTypes, Policies policies, MeterRegistry meterRegistry) {
        this.rics = rics;
        this.policyTypes = policyTypes;
        this.policies = policies;
        this.meterRegistry = meterRegistry;

        ricCount = meterRegistry.gauge("total_ric_count", new AtomicLong(0));
        policyTypeCount = meterRegistry.gauge("total_policy_type_count", new AtomicLong(0));
        policyCount = meterRegistry.gauge("total_policy_count", new AtomicLong(0));
    }

    /**
     * Every minute, updates counters for statistical purposes.
     */
    @Scheduled(fixedRate = 1000 * 60)
    public void checkAllCounters() {
        logger.trace("Checking counters starting...");
        ricCount.set(rics.size());
        policyCount.set(policies.size());
        policyTypeCount.set(policyTypes.size());
    }
}
