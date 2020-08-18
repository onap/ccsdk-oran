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

package org.onap.ccsdk.oran.a1policymanagementservice.utils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

public class MockA1Client implements A1Client {
    Policies policies = new Policies();
    private final PolicyTypes policyTypes;
    private final Duration asynchDelay;

    public MockA1Client(PolicyTypes policyTypes, Duration asynchDelay) {
        this.policyTypes = policyTypes;
        this.asynchDelay = asynchDelay;
    }

    @Override
    public Mono<List<String>> getPolicyTypeIdentities() {
        List<String> result = new Vector<>();
        for (PolicyType p : this.policyTypes.getAll()) {
            result.add(p.id());
        }
        return mono(result);
    }

    @Override
    public Mono<List<String>> getPolicyIdentities() {
        Vector<String> result = new Vector<>();
        for (Policy policy : policies.getAll()) {
            result.add(policy.id());
        }

        return mono(result);
    }

    @Override
    public Mono<String> getPolicyTypeSchema(String policyTypeId) {
        try {
            return mono(this.policyTypes.getType(policyTypeId).schema());
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<String> putPolicy(Policy p) {
        this.policies.put(p);
        return mono("OK");

    }

    @Override
    public Mono<String> deletePolicy(Policy policy) {
        this.policies.remove(policy);
        return mono("OK");
    }

    public Policies getPolicies() {
        return this.policies;
    }

    @Override
    public Mono<A1ProtocolType> getProtocolVersion() {
        return mono(A1ProtocolType.STD_V1_1);
    }

    @Override
    public Flux<String> deleteAllPolicies() {
        this.policies.clear();
        return mono("OK") //
            .flatMapMany(Flux::just);
    }

    @Override
    public Mono<String> getPolicyStatus(Policy policy) {
        return mono("OK");
    }

    private <T> Mono<T> mono(T value) {
        if (this.asynchDelay.isZero()) {
            return Mono.just(value);
        } else {
            return Mono.create(monoSink -> asynchResponse(monoSink, value));
        }
    }

    Mono<String> monoError(String responseBody, HttpStatus status) {
        byte[] responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        WebClientResponseException a1Exception = new WebClientResponseException(status.value(),
            status.getReasonPhrase(), null, responseBodyBytes, StandardCharsets.UTF_8, null);
        return Mono.error(a1Exception);
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    private void sleep() {
        try {
            Thread.sleep(this.asynchDelay.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private <T> void asynchResponse(MonoSink<T> callback, T str) {
        Thread thread = new Thread(() -> {
            sleep(); // Simulate a network delay
            callback.success(str);
        });
        thread.start();
    }

}
