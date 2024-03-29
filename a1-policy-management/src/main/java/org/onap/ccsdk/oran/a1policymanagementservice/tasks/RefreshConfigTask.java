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

import com.google.gson.JsonObject;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

import lombok.AccessLevel;
import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

/**
 * Regularly refreshes the component configuration from a configuration file.
 */
@Component
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class RefreshConfigTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("#{systemEnvironment}")
    public Properties systemEnvironment;

    /**
     * The time between refreshes of the configuration. Not final so tests can
     * modify it.
     */
    private static Duration configRefreshInterval = Duration.ofMinutes(1);

    final ConfigurationFile configurationFile;
    final ApplicationConfig appConfig;

    @Getter(AccessLevel.PROTECTED)
    private Disposable refreshTask = null;

    final Rics rics;
    private final A1ClientFactory a1ClientFactory;
    private final Policies policies;
    private final Services services;
    private final PolicyTypes policyTypes;
    private final AsyncRestClientFactory restClientFactory;

    private long fileLastModified = 0;

    public RefreshConfigTask(ConfigurationFile configurationFile, ApplicationConfig appConfig, Rics rics,
            Policies policies, Services services, PolicyTypes policyTypes, A1ClientFactory a1ClientFactory,
            SecurityContext securityContext) {
        this.configurationFile = configurationFile;
        this.appConfig = appConfig;
        this.rics = rics;
        this.policies = policies;
        this.services = services;
        this.policyTypes = policyTypes;
        this.a1ClientFactory = a1ClientFactory;
        this.restClientFactory = new AsyncRestClientFactory(appConfig.getWebClientConfig(), securityContext);
    }

    public void start() {
        logger.debug("Starting refreshConfigTask");
        stop();
        refreshTask = createRefreshTask() //
                .subscribe(
                        notUsed -> logger.debug("Refreshed configuration data"), throwable -> logger
                                .error("Configuration refresh terminated due to exception {}", throwable.getMessage()),
                        () -> logger.error("Configuration refresh terminated"));
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.dispose();
        }
    }

    Flux<RicConfigUpdate.Type> createRefreshTask() {
        Flux<JsonObject> loadFromFile = regularInterval() //
                .flatMap(notUsed -> loadConfigurationFromFile()) //
                .onErrorResume(this::ignoreErrorFlux) //
                .doOnNext(json -> logger.debug("loadFromFile succeeded")) //
                .doOnTerminate(() -> logger.error("loadFromFile Terminate"));

        final int CONCURRENCY = 50; // Number of RIC synched in paralell

        return loadFromFile //
                .flatMap(this::parseConfiguration) //
                .flatMap(this::updateConfig, CONCURRENCY) //
                .flatMap(this::handleUpdatedRicConfig) //
                .doOnError(t -> logger.error("Cannot update config {}", t.getMessage()))
                .doFinally(signal -> logger.error("Configuration refresh task is terminated: {}", signal));
    }

    private Flux<Long> regularInterval() {
        return Flux.interval(Duration.ZERO, configRefreshInterval) //
                .onBackpressureDrop() //
                .limitRate(1); // Limit so that only one event is emitted at a time
    }

    private <R> Flux<R> ignoreErrorFlux(Throwable throwable) {
        String errMsg = throwable.toString();
        logger.warn("Could not refresh application configuration. {}", errMsg);
        return Flux.empty();
    }

    private Mono<ApplicationConfigParser.ConfigParserResult> parseConfiguration(JsonObject jsonObject) {
        try {
            ApplicationConfigParser parser = new ApplicationConfigParser(this.appConfig);
            return Mono.just(parser.parse(jsonObject));
        } catch (Exception e) {
            String str = e.toString();
            logger.error("Could not parse configuration {}", str);
            return Mono.empty();
        }
    }

    private Flux<RicConfigUpdate> updateConfig(ApplicationConfigParser.ConfigParserResult config) {
        return this.appConfig.setConfiguration(config);
    }

    private void removePoliciciesInRic(@Nullable Ric ric) {
        if (ric != null) {
            ric.getLock().lock(LockType.EXCLUSIVE, "removedRic") //
                    .flatMap(notUsed -> synchronizationTask().synchronizeRic(ric)) //
                    .doFinally(sig -> ric.getLock().unlockBlocking()) //
                    .subscribe();
        }
    }

    private RicSynchronizationTask synchronizationTask() {
        return new RicSynchronizationTask(a1ClientFactory, policyTypes, policies, services, restClientFactory, rics);
    }

    public Mono<RicConfigUpdate.Type> handleUpdatedRicConfig(RicConfigUpdate updatedInfo) {
        synchronized (this.rics) {
            String ricId = updatedInfo.getRicConfig().getRicId();
            RicConfigUpdate.Type event = updatedInfo.getType();
            if (event == RicConfigUpdate.Type.ADDED) {
                logger.debug("RIC added {}", ricId);
                Ric ric = new Ric(updatedInfo.getRicConfig());

                return ric.getLock().lock(LockType.EXCLUSIVE, "addedRic") //
                        .doOnNext(grant -> this.rics.put(ric)) //
                        .flatMapMany(grant -> this.policies.restoreFromDatabase(ric, this.policyTypes)) //
                        .collectList() //
                        .doOnNext(l -> logger.debug("Starting sycnhronization for new RIC: {}", ric.id())) //
                        .flatMap(grant -> synchronizationTask().synchronizeRic(ric)) //
                        .map(notUsed -> event) //
                        .doFinally(sig -> ric.getLock().unlockBlocking());
            } else if (event == RicConfigUpdate.Type.REMOVED) {
                logger.debug("RIC removed {}", ricId);
                Ric ric = rics.remove(ricId);
                this.policies.removePoliciesForRic(ricId);
                removePoliciciesInRic(ric);
            } else if (event == RicConfigUpdate.Type.CHANGED) {
                logger.debug("RIC config updated {}", ricId);
                Ric ric = this.rics.get(ricId);
                if (ric == null) {
                    logger.error("An non existing RIC config is changed, should not happen (just for robustness)");
                    this.rics.put(new Ric(updatedInfo.getRicConfig()));
                } else {
                    ric.setRicConfig(updatedInfo.getRicConfig());
                }
            }
            return Mono.just(event);
        }
    }

    /**
     * Reads the configuration from file.
     */
    Flux<JsonObject> loadConfigurationFromFile() {
        if (configurationFile.getLastModified() == fileLastModified) {
            return Flux.empty();
        }
        fileLastModified = configurationFile.getLastModified();
        Optional<JsonObject> readJson = configurationFile.readFile();
        if (readJson.isPresent()) {
            return Flux.just(readJson.get());
        }
        return Flux.empty();
    }
}
