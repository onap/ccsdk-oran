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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import java.time.Instant;
import java.util.Arrays;
import java.util.Vector;

import org.json.JSONObject;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import reactor.core.publisher.Mono;

public class A1ClientHelper {

    private A1ClientHelper() {
    }

    protected static Mono<String> createOutputJsonResponse(String key, String value) {
        JSONObject paramsJson = new JSONObject();
        paramsJson.put(key, value);
        JSONObject responseJson = new JSONObject();
        responseJson.put("output", paramsJson);
        return Mono.just(responseJson.toString());
    }

    protected static Ric createRic(String url) {
        RicConfig cfg = ImmutableRicConfig.builder().ricId("ric") //
            .baseUrl(url) //
            .managedElementIds(new Vector<String>(Arrays.asList("kista_1", "kista_2"))) //
            .controllerName("") //
            .build();
        return new Ric(cfg);
    }

    protected static Policy createPolicy(String nearRtRicUrl, String policyId, String json, String type) {
        return ImmutablePolicy.builder() //
            .id(policyId) //
            .json(json) //
            .ownerServiceId("service") //
            .ric(createRic(nearRtRicUrl)) //
            .type(createPolicyType(type)) //
            .lastModified(Instant.now()) //
            .isTransient(false) //
            .build();
    }

    protected static PolicyType createPolicyType(String name) {
        return ImmutablePolicyType.builder().id(name).schema("schema").build();
    }

    protected static String getCreateSchema(String policyType, String policyTypeId) {
        JSONObject obj = new JSONObject(policyType);
        JSONObject schemaObj = obj.getJSONObject("create_schema");
        schemaObj.put("title", policyTypeId);
        return schemaObj.toString();
    }
}
