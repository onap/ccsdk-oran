/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.time.Duration;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Periodically checks that services with a keepAliveInterval set are alive. If
 * a service is deemed not alive, all the service's policies are deleted, both
 * in the repository and in the affected Rics, and the service is removed from
 * the repository. This means that the service needs to register again after
 * this.
 */
@Component
@EnableScheduling
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class ServiceSupervision {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    static final int CONCURRENCY_RIC = 1; // How may paralell requests that is sent
    private final Services services;
    private final Policies policies;
    private A1ClientFactory a1ClientFactory;
    private final Duration checkInterval;

    @Autowired
    public ServiceSupervision(Services services, Policies policies, A1ClientFactory a1ClientFactory) {
        this(services, policies, a1ClientFactory, Duration.ofMinutes(1));
    }

    public ServiceSupervision(Services services, Policies policies, A1ClientFactory a1ClientFactory,
            Duration checkInterval) {
        this.services = services;
        this.policies = policies;
        this.a1ClientFactory = a1ClientFactory;
        this.checkInterval = checkInterval;
        start();
    }

    private void start() {
        logger.debug("Checking services starting");
        createTask().subscribe(null, null, () -> logger.error("Checking services unexpectedly terminated"));
    }

    private Flux<?> createTask() {
        return Flux.interval(this.checkInterval) //
                .flatMap(notUsed -> checkAllServices());
    }

    Flux<Policy> checkAllServices() {
        return Flux.fromIterable(services.getAll()) //
                .filter(Service::isExpired) //
                .doOnNext(service -> logger.info("Service is expired: {}", service.getName())) //
                .doOnNext(service -> services.remove(service.getName())) //
                .flatMap(this::getAllPoliciesForService) //
                .flatMap(this::deletePolicy, CONCURRENCY_RIC);
    }

    @SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
    private Flux<Policy> deletePolicy(Policy policy) {
        Lock lock = policy.getRic().getLock();
        return lock.lock(LockType.SHARED, "ServiceSupervision") //
                .doOnNext(notUsed -> policies.remove(policy)) //
                .flatMap(notUsed -> deletePolicyInRic(policy))
                .doOnNext(notUsed -> logger.debug("Policy deleted due to service inactivity: {}, service: {}",
                        policy.getId(), policy.getOwnerServiceId())) //
                .doFinally(notUsed -> lock.unlockBlocking()) //
                .doOnError(throwable -> logger.debug("Failed to delete inactive policy: {}, reason: {}", policy.getId(),
                        throwable.getMessage())) //
                .flatMapMany(notUsed -> Flux.just(policy)) //
                .onErrorResume(throwable -> Flux.empty());
    }

    private Flux<Policy> getAllPoliciesForService(Service service) {
        return Flux.fromIterable(policies.getForService(service.getName()));
    }

    private Mono<Policy> deletePolicyInRic(Policy policy) {
        return a1ClientFactory.createA1Client(policy.getRic()) //
                .flatMap(client -> client.deletePolicy(policy) //
                        .onErrorResume(exception -> handleDeleteFromRicFailure(policy, exception)) //
                        .map(nothing -> policy));
    }

    private Mono<String> handleDeleteFromRicFailure(Policy policy, Throwable e) {
        logger.warn("Could not delete policy: {} from ric: {}. Cause: {}", policy.getId(), policy.getRic().id(),
                e.getMessage());
        return Mono.empty();
    }
}
