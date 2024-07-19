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

package org.onap.ccsdk.oran.a1policymanagementservice.tasks;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
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
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int CONCURRENCY = 50; // Number of RIC checked in paralell
    private final Rics rics;
    private final Policies policies;
    private final PolicyTypes policyTypes;
    private final A1ClientFactory a1ClientFactory;
    private final Services services;
    private final AsyncRestClientFactory restClientFactory;

    private static class SynchNeededException extends ServiceException {
        private static final long serialVersionUID = 1L;

        public SynchNeededException(RicData ric) {
            super("SynchNeededException for " + ric.ric.id());
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

    public RicSupervision(Rics rics, Policies policies, A1ClientFactory a1ClientFactory, PolicyTypes policyTypes,
            Services services, ApplicationConfig config, SecurityContext securityContext) {
        this.rics = rics;
        this.policies = policies;
        this.a1ClientFactory = a1ClientFactory;
        this.policyTypes = policyTypes;
        this.services = services;
        this.restClientFactory = new AsyncRestClientFactory(config.getWebClientConfig(), securityContext);
    }

    /**
     * Regularly contacts all Rics to check if they are alive and synchronized.
     */
    @Scheduled(fixedRate = 1000 * 60)
    public void checkAllRics() {
        logger.debug("Checking Rics starting");
        createTask().subscribe(null, null, () -> logger.debug("Checking all RICs completed"));
    }

    private Flux<Ric> createTask() {
        return Flux.fromIterable(rics.getRics()) //
                .flatMap(this::createRicData) //
                .onErrorResume(t -> Flux.empty()) //
                .flatMap(this::checkOneRic, CONCURRENCY) //
                .map(ricData -> ricData.ric);
    }

    private Mono<RicData> checkOneRic(RicData ricData) {
        if (ricData.ric.getState() == RicState.CONSISTENCY_CHECK || ricData.ric.getState() == RicState.SYNCHRONIZING) {
            logger.debug("Skipping check ric: {}, state: {}", ricData.ric.id(), ricData.ric.getState());
            return Mono.empty(); // Skip, already in progress
        }
        return ricData.ric.getLock().lock(LockType.EXCLUSIVE, "checkOneRic") //
                .flatMap(lock -> synchIfUnavailable(ricData)) //
                .doOnNext(ric -> ricData.ric.setState(RicState.CONSISTENCY_CHECK)) //
                .flatMap(x -> checkRicPolicies(ricData)) //
                .flatMap(x -> checkRicPolicyTypes(ricData)) //
                .doOnNext(x -> onRicCheckedOk(ricData)) //
                .onErrorResume(t -> onRicCheckedError(t, ricData)) //
                .doFinally(sig -> ricData.ric.getLock().unlockBlocking()) //
                .onErrorResume(throwable -> Mono.empty());
    }

    private Mono<RicData> synchIfUnavailable(RicData ric) {
        if (ric.ric.getState() == RicState.UNAVAILABLE) {
            return Mono.error(new SynchNeededException(ric));
        } else {
            return Mono.just(ric);
        }
    }

    private Mono<RicData> onRicCheckedError(Throwable t, RicData ricData) {
        logger.debug("Ric: {} check stopped, exception: {}", ricData.ric.id(), t.getMessage());
        ricData.ric.setState(RicState.UNAVAILABLE);
        if ((t instanceof SynchNeededException)) {
            return startSynchronization(ricData);
        } else {
            logger.warn("RicSupervision, ric: {}, exception: {}", ricData.ric.id(), t.getMessage());
            return Mono.empty();
        }
    }

    private void onRicCheckedOk(RicData ricData) {
        logger.debug("Ric: {} checked OK", ricData.ric.id());
        ricData.ric.setState(RicState.AVAILABLE);
    }

    private Mono<RicData> createRicData(Ric ric) {
        return this.a1ClientFactory.createA1Client(ric) //
                .doOnError(t -> logger.debug("Could not create A1 client for ric: {}, reason: {}", ric.id(),
                        t.getMessage())) //
                .map(a1Client -> new RicData(ric, a1Client));
    }

    private Mono<RicData> checkRicPolicies(RicData ric) {
        return ric.getClient().getPolicyIdentities() //
                .flatMap(ricP -> validateInstances(ricP, ric));
    }

    private Mono<RicData> validateInstances(Collection<String> ricPolicies, RicData ric) {
        logger.trace("Policies to be validated: {} , against: {} , in ric: {}", ricPolicies, ric.ric.getManagedElementIds(), ric.ric.id());
        synchronized (this.policies) {
            if (ricPolicies.size() != policies.getForRic(ric.ric.id()).size()) {
                logger.debug("RicSupervision, starting ric: {} synchronization (noOfPolicices == {}, expected == {})",
                        ric.ric.id(), ricPolicies.size(), policies.getForRic(ric.ric.id()).size());
                return Mono.error(new SynchNeededException(ric));
            }

            for (String policyId : ricPolicies) {
                if (!policies.containsPolicy(policyId)) {
                    logger.debug("RicSupervision, starting ric: {} synchronization (unexpected policy in RIC: {})",
                            ric.ric.id(), policyId);
                    return Mono.error(new SynchNeededException(ric));
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
            return Mono.error(new SynchNeededException(ric));
        }
        for (String typeName : ricTypes) {
            if (!ric.ric.isSupportingType(typeName)) {
                logger.debug("RicSupervision, starting ric: {} synchronization (unexpected policy type: {})",
                        ric.ric.id(), typeName);
                return Mono.error(new SynchNeededException(ric));
            }
        }

        return Mono.just(ric);
    }

    private Mono<RicData> startSynchronization(RicData ric) {
        logger.debug("RicSupervision, starting ric: {} synchronization, state: {}", ric.ric.id(), ric.ric.getState());
        RicSynchronizationTask synchronizationTask = createSynchronizationTask();
        return synchronizationTask.synchronizeRic(ric.ric) //
                .flatMap(notUsed -> Mono.just(ric));

    }

    RicSynchronizationTask createSynchronizationTask() {
        return new RicSynchronizationTask(a1ClientFactory, policyTypes, policies, services, restClientFactory, rics);
    }
}
