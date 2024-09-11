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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RicSupervision;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1ClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Invoke operations over the NBI and start synchronizations in a separate
 * thread. For test of robustness using concurrent clients.
 */
class ConcurrencyTestRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AsyncRestClient webClient;
    static AtomicInteger nextCount = new AtomicInteger(0);
    private final int count;
    private final RicSupervision supervision;
    private final MockA1ClientFactory a1ClientFactory;
    private final Rics rics;
    private final PolicyTypes types;
    private boolean failed = false;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static Gson gson = new GsonBuilder().create();

    ConcurrencyTestRunnable(AsyncRestClient client, RicSupervision supervision, MockA1ClientFactory a1ClientFactory,
            Rics rics, PolicyTypes types) {
        this.count = nextCount.incrementAndGet();
        this.supervision = supervision;
        this.a1ClientFactory = a1ClientFactory;
        this.rics = rics;
        this.types = types;
        this.webClient = client;
    }

    private void printStatusInfo() {
        try {
            String url = "/actuator/metrics/jvm.threads.live";
            ResponseEntity<String> result = webClient.getForEntity(url).block();
            System.out.println(Thread.currentThread() + result.getBody());

            url = "/rics";
            result = webClient.getForEntity(url).block();
            System.out.println(Thread.currentThread() + result.getBody());

        } catch (Exception e) {
            logger.error("{} Concurrency test printStatusInfo exception {}", Thread.currentThread(), e.toString());
        }
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < 500; ++i) {
                if (i % 100 == 0) {
                    createInconsistency();
                    this.supervision.checkAllRics();
                }
                String name = "policy_" + count + "_" + i;
                putPolicy(name);
                putPolicy(name + "-");
                listPolicies();
                listTypes();
                deletePolicy(name);
                deletePolicy(name + "-");
            }
        } catch (Exception e) {
            logger.error("Concurrency test exception {}", e.toString());
            printStatusInfo();
            failed = true;
        }
    }

    public boolean isFailed() {
        return this.failed;
    }

    private Policy createPolicyObject(String id) {
        Ric ric = this.rics.get("ric");
        PolicyType type = this.types.get("type1");
        return Policy.builder() //
                .id(id) //
                .json("{}") //
                .type(type) //
                .ric(ric) //
                .ownerServiceId("") //
                .lastModified(Instant.now()) //
                .isTransient(false) //
                .statusNotificationUri("/policy_status?id=XXX") //
                .build();
    }

    private void createInconsistency() {
        MockA1Client client = a1ClientFactory.getOrCreateA1Client("ric");
        Policy policy = createPolicyObject("junk");
        client.putPolicy(policy).block();
    }

    private void listPolicies() {
        String uri = "/policy-instances";
        webClient.getForEntity(uri).block();
    }

    private void listTypes() {
        String uri = "/policy-types";
        webClient.getForEntity(uri).block();
    }

    private void putPolicy(String name) throws JsonProcessingException {
        String putUrl = "/policies";
        String body = putPolicyBody("service1", "ric", "type1", name, false);
        webClient.putForEntity(putUrl, body).block();
    }

    private String putPolicyBody(String serviceName, String ricId, String policyTypeName, String policyInstanceId,
            boolean isTransient) throws JsonProcessingException {

        PolicyInfo policyInfo = new PolicyInfo(ricId, policyInstanceId, policyData(), policyTypeName);
        policyInfo.setServiceId(serviceName);
        policyInfo.setStatusNotificationUri("/status");
        policyInfo.setTransient(isTransient);
        return objectMapper.writeValueAsString(policyInfo);
    }

    private Map<String,String> policyData() {
        Map<String,String> policyDataInMap = new HashMap<>();
        policyDataInMap.put("servingCellNrcgi","1");
        return policyDataInMap;
    }

    private void deletePolicy(String name) {
        String deleteUrl = "/policies/" + name;
        webClient.delete(deleteUrl).block();
    }
}
