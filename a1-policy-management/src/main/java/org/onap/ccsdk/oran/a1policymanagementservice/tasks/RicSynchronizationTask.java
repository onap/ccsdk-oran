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

import java.util.Set;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.ServiceCallbacks;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    public Mono<Ric> synchronizeRic(Ric ric) {
        if (ric.getLock().getLockCounter() != 1) {
            logger.error("Exclusive lock is required to run synchronization, ric: {}", ric.id());
            return Mono.empty();
        }
        return this.a1ClientFactory.createA1Client(ric) //
                .doOnNext(client -> ric.setState(RicState.SYNCHRONIZING)) //
                .flatMapMany(client -> runSynchronization(ric, client)) //
                .doOnError(t -> { //
                    logger.warn("Synchronization failure for ric: {}, reason: {}", ric.id(), t.getMessage()); //
                    deletePoliciesIfNotRecreatable(t, ric);
                }) //
                .collectList() //
                .flatMap(notUsed -> onSynchronizationComplete(ric)) //
                .onErrorResume(t -> Mono.just(ric)) //
                .doFinally(signal -> onFinally(signal, ric));
    }

    private void onFinally(SignalType signal, Ric ric) {
        if (ric.getState().equals(RicState.SYNCHRONIZING)) {
            logger.debug("Resetting ric state after failed synch, ric: {}, signal: {}", ric.id(), signal);
            ric.setState(RicState.UNAVAILABLE); //
        }
    }

    /**
     * If a 4xx error is received, allpolicies are deleted. This is just to avoid
     * cyclical receovery due to that the NearRT RIC cannot accept a previously
     * policy.
     */
    private void deletePoliciesIfNotRecreatable(Throwable throwable, Ric ric) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException responseException = (WebClientResponseException) throwable;
            if (responseException.getStatusCode().is4xxClientError()) {
                deleteAllPoliciesInRepository(ric);
            }
        }
    }

    private void deleteAllPoliciesInRepository(Ric ric) {
        for (Policy policy : policies.getForRic(ric.id())) {
            this.policies.remove(policy);
        }
    }

    public Flux<PolicyType> synchronizePolicyTypes(Ric ric, A1Client a1Client) {
        return a1Client.getPolicyTypeIdentities() //
                .doOnNext(x -> ric.clearSupportedPolicyTypes()) //
                .flatMapMany(Flux::fromIterable) //
                .doOnNext(typeId -> logger.debug("For ric: {}, handling type: {}", ric.getConfig().getRicId(), typeId)) //
                .flatMap(policyTypeId -> getPolicyType(policyTypeId, a1Client), CONCURRENCY_RIC) //
                .doOnNext(ric::addSupportedPolicyType); //
    }

    private Flux<Object> runSynchronization(Ric ric, A1Client a1Client) {
        Flux<PolicyType> synchronizedTypes = synchronizePolicyTypes(ric, a1Client);
        Set<String> excludeFromDelete = this.policies.getPolicyIdsForRic(ric.id());
        Flux<?> policiesDeletedInRic = a1Client.deleteAllPolicies(excludeFromDelete);
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

    private Flux<Policy> putPolicy(Policy policy, Ric ric, A1Client a1Client) {
        logger.trace("Recreating policy: {}, for ric: {}", policy.getId(), ric.getConfig().getRicId());
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
