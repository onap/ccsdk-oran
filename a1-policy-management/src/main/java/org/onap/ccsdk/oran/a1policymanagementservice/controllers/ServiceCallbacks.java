/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2020 Nordix Foundation
 * %%
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
        this.restClient = restClientFactory.createRestClient("");
    }

    public void notifyServicesRicSynchronized(Ric ric, Services services) {
        createTask(ric, services).subscribe(numberOfServices -> logger.debug("Services {} notified", numberOfServices),
                throwable -> logger.error("Service notification failed, cause: {}", throwable.getMessage()),
                () -> logger.debug("All services notified"));

    }

    private Mono<Integer> createTask(Ric ric, Services services) {
        return Flux.fromIterable(services.getAll()) //
                .flatMap(service -> notifyServiceRicSynchronized(ric, service)) //
                .collectList() //
                .flatMap(okResponses -> Mono.just(Integer.valueOf(okResponses.size()))); //
    }

    private Mono<String> notifyServiceRicSynchronized(Ric ric, Service service) {
        if (service.getCallbackUrl().isEmpty()) {
            return Mono.empty();
        }

        ServiceCallbackInfo request = new ServiceCallbackInfo(ric.id(), ServiceCallbackInfo.EventType.AVAILABLE);
        String body = gson.toJson(request);

        return restClient.post(service.getCallbackUrl(), body)
                .doOnNext(resp -> logger.debug("Invoking service {} callback,   ric: {}", service.getName(), ric.id()))
                .onErrorResume(throwable -> {
                    logger.error("Service: {}, callback: {} failed:  {}", service.getName(), service.getCallbackUrl(),
                            throwable.toString());
                    return Mono.empty();
                });
    }

}
