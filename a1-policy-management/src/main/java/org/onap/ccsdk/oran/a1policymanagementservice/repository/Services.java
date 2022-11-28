/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2022 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import com.google.gson.Gson;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.datastore.DataStore;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Services {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static Gson gson = Service.createGson();
    private final DataStore dataStore;

    private Map<String, Service> registeredServices = new HashMap<>();

    public Services(@Autowired ApplicationConfig appConfig) {
        this.dataStore = DataStore.create(appConfig, "services");
    }

    public synchronized Service getService(String name) throws ServiceException {
        Service service = registeredServices.get(name);
        if (service == null) {
            throw new ServiceException("Could not find service: " + name);
        }
        return service;
    }

    public synchronized Service get(String name) {
        return registeredServices.get(name);
    }

    public synchronized void put(Service service) {
        logger.debug("Put service: {}", service.getName());
        service.keepAlive();
        registeredServices.put(service.getName(), service);
        store(service);
    }

    public synchronized Iterable<Service> getAll() {
        return new Vector<>(registeredServices.values());
    }

    public synchronized void remove(String name) {
        Service service = registeredServices.remove(name);
        if (service != null) {
            dataStore.deleteObject(getPath(service)).subscribe();
        }
    }

    public synchronized int size() {
        return registeredServices.size();
    }

    public synchronized void clear() {
        registeredServices.clear();
        dataStore.deleteAllObjects().onErrorResume(t -> Mono.empty()).subscribe();
    }

    public void store(Service service) {
        byte[] bytes = gson.toJson(service).getBytes();
        dataStore.writeObject(getPath(service), bytes) //
                .doOnError(t -> logger.warn("Could not service: {} {}", service.getName(), t.getMessage())).subscribe();
    }

    public Flux<Service> restoreFromDatabase() {
        return dataStore.createDataStore().flatMapMany(ds -> dataStore.listObjects("")) //
                .flatMap(dataStore::readObject, 1) //
                .map(String::new) //
                .map(json -> gson.fromJson(json, Service.class))
                .doOnNext(service -> this.registeredServices.put(service.getName(), service))
                .doOnError(t -> logger.warn("Could not restore services database : {}", t.getMessage()))
                .doFinally(sig -> logger.debug("Restored type database,no of services: {}",
                        this.registeredServices.size())) //
                .onErrorResume(t -> Flux.empty()); //
    }

    private String getPath(Service service) {
        return service.getName() + ".json";
    }

}
