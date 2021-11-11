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

package org.onap.ccsdk.oran.a1policymanagementservice.tasks;

import java.util.Collection;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Regularly checks the existing rics towards the local repository to keep it
 * consistent. When the policy types or instances in the Near-RT RIC is not
 * consistent, a synchronization is performed.
 */
@Component
@EnableScheduling
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class RicSupervision {
    private static final Logger logger = LoggerFactory.getLogger(RicSupervision.class);

    private static final int CONCURRENCY = 50; // Number of RIC checked in paralell
    private final Rics rics;
    private final Policies policies;
    private final PolicyTypes policyTypes;
    private final A1ClientFactory a1ClientFactory;
    private final Services services;
    private final AsyncRestClientFactory restClientFactory;

    private static class SynchStartedException extends ServiceException {
        private static final long serialVersionUID = 1L;

        public SynchStartedException(String message) {
            super(message);
        }
    }

    private static class RicData {
        RicData(Ric ric, A1Client a1Client) {
            this.ric = ric;
            this.a1Client = a1Client;
        }

        A1Client getClient() {
            return a1Client;
        }

        final Ric ric;
        private final A1Client a1Client;
    }

    @Autowired
    public RicSupervision(Rics rics, Policies policies, A1ClientFactory a1ClientFactory, PolicyTypes policyTypes,
            Services services, ApplicationConfig config) {
        this.rics = rics;
        this.policies = policies;
        this.a1ClientFactory = a1ClientFactory;
        this.policyTypes = policyTypes;
        this.services = services;
        this.restClientFactory = new AsyncRestClientFactory(config.getWebClientConfig());
    }

    /**
     * Regularly contacts all Rics to check if they are alive and synchronized.
     */
    @Scheduled(fixedRate = 1000 * 60)
    public void checkAllRics() {
        logger.debug("Checking Rics starting");
        createTask().subscribe(null, null, () -> logger.debug("Checking all RICs completed"));
    }

    private Flux<RicData> createTask() {
        return Flux.fromIterable(rics.getRics()) //
                .flatMap(this::createRicData) //
                .flatMap(this::checkOneRic, CONCURRENCY);
    }

    private Mono<RicData> checkOneRic(RicData ricData) {
        return checkRicState(ricData) //
                .flatMap(x -> ricData.ric.getLock().lock(LockType.EXCLUSIVE)) //
                .flatMap(notUsed -> setRicState(ricData)) //
                .flatMap(x -> checkRicPolicies(ricData)) //
                .flatMap(x -> checkRicPolicyTypes(ricData)) //
                .doOnNext(x -> onRicCheckedOk(ricData)) //
                .doOnError(t -> onRicCheckedError(t, ricData)) //
                .onErrorResume(throwable -> Mono.empty());
    }

    private void onRicCheckedError(Throwable t, RicData ricData) {
        logger.debug("Ric: {} check stopped, exception: {}", ricData.ric.id(), t.getMessage());
        if (!(t instanceof SynchStartedException)) {
            // If synch is started, the synch will set the final state
            ricData.ric.setState(RicState.UNAVAILABLE);
        }
        ricData.ric.getLock().unlockBlocking();
    }

    private void onRicCheckedOk(RicData ricData) {
        logger.debug("Ric: {} checked OK", ricData.ric.id());
        ricData.ric.setState(RicState.AVAILABLE);
        ricData.ric.getLock().unlockBlocking();
    }

    @SuppressWarnings("squid:S2445") // Blocks should be synchronized on "private final" fields
    private Mono<RicData> setRicState(RicData ric) {
        synchronized (ric) {
            if (ric.ric.getState() == RicState.CONSISTENCY_CHECK) {
                logger.debug("Ric: {} is already being checked", ric.ric.getConfig().ricId());
                return Mono.empty();
            }
            ric.ric.setState(RicState.CONSISTENCY_CHECK);
            return Mono.just(ric);
        }
    }

    private Mono<RicData> createRicData(Ric ric) {
        return Mono.just(ric) //
                .flatMap(aRic -> this.a1ClientFactory.createA1Client(ric)) //
                .map(a1Client -> new RicData(ric, a1Client));
    }

    private Mono<RicData> checkRicState(RicData ric) {
        if (ric.ric.getState() == RicState.UNAVAILABLE) {
            logger.debug("RicSupervision, starting ric: {} synchronization (state == UNAVAILABLE)", ric.ric.id());
            return startSynchronization(ric) //
                    .onErrorResume(t -> Mono.empty());
        } else if (ric.ric.getState() == RicState.SYNCHRONIZING || ric.ric.getState() == RicState.CONSISTENCY_CHECK) {
            return Mono.empty();
        } else {
            return Mono.just(ric);
        }
    }

    private Mono<RicData> checkRicPolicies(RicData ric) {
        return ric.getClient().getPolicyIdentities() //
                .flatMap(ricP -> validateInstances(ricP, ric));
    }

    private Mono<RicData> validateInstances(Collection<String> ricPolicies, RicData ric) {
        synchronized (this.policies) {
            if (ricPolicies.size() != policies.getForRic(ric.ric.id()).size()) {
                logger.debug("RicSupervision, starting ric: {} synchronization (noOfPolicices == {}, expected == {})",
                        ric.ric.id(), ricPolicies.size(), policies.getForRic(ric.ric.id()).size());
                return startSynchronization(ric);
            }

            for (String policyId : ricPolicies) {
                if (!policies.containsPolicy(policyId)) {
                    logger.debug("RicSupervision, starting ric: {} synchronization (unexpected policy in RIC: {})",
                            ric.ric.id(), policyId);
                    return startSynchronization(ric);
                }
            }
            return Mono.just(ric);
        }
    }

    private Mono<RicData> checkRicPolicyTypes(RicData ric) {
        return ric.getClient().getPolicyTypeIdentities() //
                .flatMap(ricTypes -> validateTypes(ricTypes, ric));
    }

    private Mono<RicData> validateTypes(Collection<String> ricTypes, RicData ric) {
        if (ricTypes.size() != ric.ric.getSupportedPolicyTypes().size()) {
            logger.debug(
                    "RicSupervision, starting ric: {} synchronization (unexpected numer of policy types in RIC: {}, expected: {})",
                    ric.ric.id(), ricTypes.size(), ric.ric.getSupportedPolicyTypes().size());
            return startSynchronization(ric);
        }
        for (String typeName : ricTypes) {
            if (!ric.ric.isSupportingType(typeName)) {
                logger.debug("RicSupervision, starting ric: {} synchronization (unexpected policy type: {})",
                        ric.ric.id(), typeName);
                return startSynchronization(ric);
            }
        }
        return Mono.just(ric);
    }

    private Mono<RicData> startSynchronization(RicData ric) {
        RicSynchronizationTask synchronizationTask = createSynchronizationTask();
        return synchronizationTask.synchronizeRic(ric.ric) //
                .flatMap(notUsed -> Mono.error(new SynchStartedException("Syncronization started")));

    }

    RicSynchronizationTask createSynchronizationTask() {
        return new RicSynchronizationTask(a1ClientFactory, policyTypes, policies, services, restClientFactory, rics);
    }
}
