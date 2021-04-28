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

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

import lombok.AccessLevel;
import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ConfigurationFile;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.ServiceCallbacks;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.api.CbsClient;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.api.CbsClientFactory;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.api.CbsRequests;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.model.CbsRequest;
import org.onap.dcaegen2.services.sdk.rest.services.cbs.client.model.EnvProperties;
import org.onap.dcaegen2.services.sdk.rest.services.model.logging.RequestDiagnosticContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

/**
 * Regularly refreshes the configuration from Consul or from a local
 * configuration file.
 */
@Component
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class RefreshConfigTask {

    private static final Logger logger = LoggerFactory.getLogger(RefreshConfigTask.class);

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
    private boolean isConsulUsed = false;

    private final Rics rics;
    private final A1ClientFactory a1ClientFactory;
    private final Policies policies;
    private final Services services;
    private final PolicyTypes policyTypes;
    private final AsyncRestClientFactory restClientFactory;

    @Autowired
    public RefreshConfigTask(ConfigurationFile configurationFile, ApplicationConfig appConfig, Rics rics,
            Policies policies, Services services, PolicyTypes policyTypes, A1ClientFactory a1ClientFactory) {
        this.configurationFile = configurationFile;
        this.appConfig = appConfig;
        this.rics = rics;
        this.policies = policies;
        this.services = services;
        this.policyTypes = policyTypes;
        this.a1ClientFactory = a1ClientFactory;
        this.restClientFactory = new AsyncRestClientFactory(appConfig.getWebClientConfig());
    }

    public void start() {
        logger.debug("Starting refreshConfigTask");
        stop();
        refreshTask = createRefreshTask() //
                .subscribe(
                        notUsed -> logger.debug("Refreshed configuration data"), throwable -> logger
                                .error("Configuration refresh terminated due to exception {}", throwable.toString()),
                        () -> logger.error("Configuration refresh terminated"));
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.dispose();
        }
    }

    Flux<RicConfigUpdate.Type> createRefreshTask() {
        Flux<JsonObject> loadFromFile = regularInterval() //
                .filter(notUsed -> !this.isConsulUsed) //
                .flatMap(notUsed -> loadConfigurationFromFile()) //
                .onErrorResume(this::ignoreErrorFlux) //
                .doOnNext(json -> logger.debug("loadFromFile succeeded")) //
                .doOnTerminate(() -> logger.error("loadFromFile Terminate"));

        Flux<JsonObject> loadFromConsul = regularInterval() //
                .flatMap(i -> getEnvironment(systemEnvironment)) //
                .flatMap(this::createCbsClient) //
                .flatMap(this::getFromCbs) //
                .onErrorResume(this::ignoreErrorMono) //
                .doOnNext(json -> logger.debug("loadFromConsul succeeded")) //
                .doOnNext(json -> this.isConsulUsed = true) //
                .doOnTerminate(() -> logger.error("loadFromConsul Terminated"));

        final int CONCURRENCY = 50; // Number of RIC synched in paralell

        return Flux.merge(loadFromFile, loadFromConsul) //
                .flatMap(this::parseConfiguration) //
                .flatMap(this::updateConfig, CONCURRENCY) //
                .flatMap(this::handleUpdatedRicConfig) //
                .doOnTerminate(() -> logger.error("Configuration refresh task is terminated"));
    }

    private Flux<Long> regularInterval() {
        return Flux.interval(Duration.ZERO, configRefreshInterval) //
                .onBackpressureDrop() //
                .limitRate(1); // Limit so that only one event is emitted at a time
    }

    Mono<EnvProperties> getEnvironment(Properties systemEnvironment) {
        return EnvironmentProcessor.readEnvironmentVariables(systemEnvironment) //
                .onErrorResume(t -> Mono.empty());
    }

    Mono<CbsClient> createCbsClient(EnvProperties env) {
        return CbsClientFactory.createCbsClient(env) //
                .onErrorResume(this::ignoreErrorMono);
    }

    private Mono<JsonObject> getFromCbs(CbsClient cbsClient) {
        try {
            final CbsRequest getConfigRequest = CbsRequests.getAll(RequestDiagnosticContext.create());
            return cbsClient.get(getConfigRequest) //
                    .onErrorResume(this::ignoreErrorMono);
        } catch (Exception e) {
            return ignoreErrorMono(e);
        }
    }

    private <R> Flux<R> ignoreErrorFlux(Throwable throwable) {
        String errMsg = throwable.toString();
        logger.warn("Could not refresh application configuration. {}", errMsg);
        return Flux.empty();
    }

    private <R> Mono<R> ignoreErrorMono(Throwable throwable) {
        String errMsg = throwable.toString();
        logger.warn("Could not refresh application configuration. {}", errMsg);
        return Mono.empty();
    }

    private Mono<ApplicationConfigParser.ConfigParserResult> parseConfiguration(JsonObject jsonObject) {
        try {
            ApplicationConfigParser parser = new ApplicationConfigParser();
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
            synchronizationTask().run(ric);
        }
    }

    private RicSynchronizationTask synchronizationTask() {
        return new RicSynchronizationTask(a1ClientFactory, policyTypes, policies, services, restClientFactory, rics);
    }

    /**
     * for an added RIC after a restart it is nesessary to get the suypported policy
     * types from the RIC unless a full synchronization is wanted.
     * 
     * @param ric the ric to get supprted types from
     * @return the same ric
     */
    private Mono<Ric> trySyncronizeSupportedTypes(Ric ric) {
        logger.debug("Synchronizing policy types for new RIC: {}", ric.id());
        // Synchronize the policy types
        return this.a1ClientFactory.createA1Client(ric) //
                .flatMapMany(client -> synchronizationTask().synchronizePolicyTypes(ric, client)) //
                .collectList() //
                .flatMap(list -> Mono.just(ric)) //
                .doOnError(t -> logger.warn("Failed to synchronize types in new RIC: {}, reason: {}", ric.id(),
                        t.getMessage())) //
                .onErrorResume(t -> Mono.just(ric));
    }

    public Mono<RicConfigUpdate.Type> handleUpdatedRicConfig(RicConfigUpdate updatedInfo) {
        synchronized (this.rics) {
            String ricId = updatedInfo.getRicConfig().ricId();
            RicConfigUpdate.Type event = updatedInfo.getType();
            if (event == RicConfigUpdate.Type.ADDED) {
                logger.debug("RIC added {}", ricId);
                Ric ric = new Ric(updatedInfo.getRicConfig());
                return trySyncronizeSupportedTypes(ric) //
                        .flatMap(this::addRic) //
                        .flatMap(this::notifyServicesRicAvailable) //
                        .flatMap(notUsed -> Mono.just(event));
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
                    addRic(new Ric(updatedInfo.getRicConfig())).block();
                } else {
                    ric.setRicConfig(updatedInfo.getRicConfig());
                }
            }
            return Mono.just(event);
        }
    }

    Mono<Ric> addRic(Ric ric) {
        this.rics.put(ric);
        if (this.appConfig.getVardataDirectory() != null) {
            this.policies.restoreFromDatabase(ric, this.policyTypes);
        }
        logger.debug("Added RIC: {}", ric.id());

        ric.setState(RicState.AVAILABLE);

        return Mono.just(ric);
    }

    private Mono<Ric> notifyServicesRicAvailable(Ric ric) {
        ServiceCallbacks callbacks = new ServiceCallbacks(this.restClientFactory);
        return callbacks.notifyServicesRicAvailable(ric, services) //
                .collectList() //
                .flatMap(list -> Mono.just(ric));
    }

    /**
     * Reads the configuration from file.
     */
    Flux<JsonObject> loadConfigurationFromFile() {
        Optional<JsonObject> readJson = configurationFile.readFile();
        if (readJson.isPresent()) {
            return Flux.just(readJson.get());
        }
        return Flux.empty();
    }
}
