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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Callbacks to the Service
 */
@SuppressWarnings("java:S3457") // No need to call "toString()" method as formatting and string ..
public class ServiceCallbacks {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static Gson gson = new GsonBuilder().create();

    private final AsyncRestClient restClient;

    public ServiceCallbacks(AsyncRestClientFactory restClientFactory) {
        this.restClient = restClientFactory.createRestClientNoHttpProxy("");
    }

    public Flux<Service> notifyServicesRicAvailable(Ric ric, Services services) {
        final int CONCURRENCY = 10;
        return Flux.fromIterable(services.getAll()) //
                .flatMap(service -> notifyService(ric, service, ServiceCallbackInfo.EventType.AVAILABLE), CONCURRENCY); //
    }

    private Mono<Service> notifyService(Ric ric, Service service, ServiceCallbackInfo.EventType eventType) {
        if (service.getCallbackUrl().isEmpty()) {
            return Mono.empty();
        }

        ServiceCallbackInfo request = new ServiceCallbackInfo(ric.id(), eventType);
        String body = gson.toJson(request);

        return restClient.post(service.getCallbackUrl(), body)
                .doOnNext(resp -> logger.debug("Invoking service {} callback,   ric: {}", service.getName(), ric.id()))
                .onErrorResume(throwable -> {
                    logger.warn("Service: {}, callback: {} failed:  {}", service.getName(), service.getCallbackUrl(),
                            throwable.toString());
                    return Mono.empty();
                }) //
                .flatMap(resp -> Mono.just(service));
    }

}
