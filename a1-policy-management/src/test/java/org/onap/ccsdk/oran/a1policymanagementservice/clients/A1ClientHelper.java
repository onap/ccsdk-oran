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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import java.time.Instant;
import java.util.Arrays;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;

public class A1ClientHelper {

    private A1ClientHelper() {}

    private static Ric createRic(String url) {
        RicConfig cfg = RicConfig.builder().ricId("ric") //
                .baseUrl(url) //
                .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))) //
                .build();
        return new Ric(cfg);
    }

    public static Policy createPolicy(String nearRtRicUrl, String policyId, String json, String type) {
        String callbackUrl = "https://test.com";
        return Policy.builder() //
                .id(policyId) //
                .json(json) //
                .ownerServiceId("service") //
                .ric(createRic(nearRtRicUrl)) //
                .type(createPolicyType(type)) //
                .lastModified(Instant.now()) //
                .isTransient(false) //
                .statusNotificationUri(callbackUrl) //
                .build();
    }

    public static PolicyType createPolicyType(String name) {
        return PolicyType.builder().id(name).schema("schema").build();
    }
}
