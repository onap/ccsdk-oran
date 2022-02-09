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

import static org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.ServiceCallbacks;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Synchronizes the content of a Near-RT RIC with the content in the repository.
 * This means:
 * <p>
 * load all policy types
 * <p>
 * send all policy instances to the Near-RT RIC
 * <p>
 * if that fails remove all policy instances
 * <p>
 * Notify subscribing services
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class RicSynchronizationTask {

    private static final Logger logger = LoggerFactory.getLogger(RicSynchronizationTask.class);
    static final int CONCURRENCY_RIC = 1; // How many paralell requests that is sent to one NearRT RIC

    private final A1ClientFactory a1ClientFactory;
    private final PolicyTypes policyTypes;
    private final Policies policies;
    private final Services services;
    private final Rics rics;
    private final AsyncRestClientFactory restClientFactory;

    public RicSynchronizationTask(A1ClientFactory a1ClientFactory, PolicyTypes policyTypes, Policies policies,
            Services services, AsyncRestClientFactory restClientFactory, Rics rics) {
        this.a1ClientFactory = a1ClientFactory;
        this.policyTypes = policyTypes;
        this.policies = policies;
        this.services = services;
        this.restClientFactory = restClientFactory;
        this.rics = rics;
    }

    public void run(Ric ric) {
        logger.debug("Ric synchronization task created: {}", ric.getConfig().ricId());

        if (ric.getState() == RicState.SYNCHRONIZING) {
            logger.debug("Ric: {} is already being synchronized", ric.getConfig().ricId());
            return;
        }

        ric.getLock().lock(LockType.EXCLUSIVE, "RicSynchronizationTask") //
                .flatMap(notUsed -> synchronizeRic(ric)) //
                .subscribe(new BaseSubscriber<Object>() {

                    @Override
                    protected void hookFinally(SignalType type) {
                        ric.getLock().unlockBlocking();
                    }
                });
    }

    public Mono<Ric> synchronizeRic(Ric ric) {
        return setRicState(ric) //
                .flatMap(lock -> this.a1ClientFactory.createA1Client(ric)) //
                .flatMapMany(client -> runSynchronization(ric, client)) //
                .onErrorResume(throwable -> deleteAllPolicyInstances(ric, throwable)) //
                .collectList() //
                .map(notUsed -> ric) //
                .doOnError(t -> { //
                    logger.warn("Synchronization failure for ric: {}, reason: {}", ric.id(), t.getMessage()); //
                    ric.setState(RicState.UNAVAILABLE); //
                }) //
                .flatMap(notUsed -> onSynchronizationComplete(ric)) //
                .onErrorResume(t -> Mono.just(ric));
    }

    public Flux<PolicyType> synchronizePolicyTypes(Ric ric, A1Client a1Client) {
        return a1Client.getPolicyTypeIdentities() //
                .doOnNext(x -> ric.clearSupportedPolicyTypes()) //
                .flatMapMany(Flux::fromIterable) //
                .doOnNext(typeId -> logger.debug("For ric: {}, handling type: {}", ric.getConfig().ricId(), typeId)) //
                .flatMap(policyTypeId -> getPolicyType(policyTypeId, a1Client), CONCURRENCY_RIC) //
                .doOnNext(ric::addSupportedPolicyType); //
    }

    @SuppressWarnings("squid:S2445") // Blocks should be synchronized on "private final" fields
    private Mono<Ric> setRicState(Ric ric) {
        synchronized (ric) {
            if (ric.getState() == RicState.SYNCHRONIZING) {
                logger.debug("Ric: {} is already being synchronized", ric.getConfig().ricId());
                return Mono.empty();
            }
            logger.debug("Ric state set to SYNCHRONIZING: {}", ric.getConfig().ricId());
            ric.setState(RicState.SYNCHRONIZING);
            return Mono.just(ric);
        }
    }

    private Flux<Object> runSynchronization(Ric ric, A1Client a1Client) {
        Flux<PolicyType> synchronizedTypes = synchronizePolicyTypes(ric, a1Client);
        Flux<?> policiesDeletedInRic = a1Client.deleteAllPolicies();
        Flux<Policy> policiesRecreatedInRic = recreateAllPoliciesInRic(ric, a1Client);

        return Flux.concat(synchronizedTypes, policiesDeletedInRic, policiesRecreatedInRic);
    }

    private Mono<Ric> onSynchronizationComplete(Ric ric) {
        if (this.rics.get(ric.id()) == null) {
            logger.debug("Policies removed in removed ric: {}", ric.id());
            return Mono.empty();
        }
        logger.debug("Synchronization completed for: {}", ric.id());
        ric.setState(RicState.AVAILABLE);
        ServiceCallbacks callbacks = new ServiceCallbacks(this.restClientFactory);
        return callbacks.notifyServicesRicAvailable(ric, services) //
                .collectList() //
                .map(list -> ric);
    }

    private Flux<Object> deleteAllPolicyInstances(Ric ric, Throwable t) {
        logger.warn("Recreation of policies failed for ric: {}, reason: {}", ric.id(), t.getMessage());
        deleteAllPoliciesInRepository(ric);

        Flux<PolicyType> synchronizedTypes = this.a1ClientFactory.createA1Client(ric) //
                .flatMapMany(a1Client -> synchronizePolicyTypes(ric, a1Client));
        Flux<?> deletePoliciesInRic = this.a1ClientFactory.createA1Client(ric) //
                .flatMapMany(A1Client::deleteAllPolicies) //
                .doOnComplete(() -> deleteAllPoliciesInRepository(ric));

        return Flux.concat(synchronizedTypes, deletePoliciesInRic);
    }

    private Mono<PolicyType> getPolicyType(String policyTypeId, A1Client a1Client) {
        if (policyTypes.contains(policyTypeId)) {
            return Mono.just(policyTypes.get(policyTypeId));
        }
        return a1Client.getPolicyTypeSchema(policyTypeId) //
                .map(schema -> createPolicyType(policyTypeId, schema));
    }

    private PolicyType createPolicyType(String policyTypeId, String schema) {
        PolicyType pt = PolicyType.builder().id(policyTypeId).schema(schema).build();
        policyTypes.put(pt);
        return pt;
    }

    private void deleteAllPoliciesInRepository(Ric ric) {
        for (Policy policy : policies.getForRic(ric.id())) {
            this.policies.remove(policy);
        }
    }

    private Flux<Policy> putPolicy(Policy policy, Ric ric, A1Client a1Client) {
        logger.trace("Recreating policy: {}, for ric: {}", policy.getId(), ric.getConfig().ricId());
        return a1Client.putPolicy(policy) //
                .flatMapMany(notUsed -> Flux.just(policy));
    }

    private boolean checkTransient(Policy policy) {
        if (policy.isTransient()) {
            this.policies.remove(policy);
        }
        return policy.isTransient();
    }

    private Flux<Policy> recreateAllPoliciesInRic(Ric ric, A1Client a1Client) {
        return Flux.fromIterable(policies.getForRic(ric.id())) //
                .doOnNext(policy -> logger.debug("Recreating policy: {}, ric: {}", policy.getId(), ric.id())) //
                .filter(policy -> !checkTransient(policy)) //
                .flatMap(policy -> putPolicy(policy, ric, a1Client), CONCURRENCY_RIC)
                .doOnError(t -> logger.warn("Recreating policy failed, ric: {}, reason: {}", ric.id(), t.getMessage()));
    }

}
