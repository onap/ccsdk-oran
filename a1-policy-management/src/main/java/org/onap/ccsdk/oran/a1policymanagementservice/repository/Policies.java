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
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

import lombok.Builder;
import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.datastore.DataStore;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class Policies {

    @Getter
    @Builder
    private static class PersistentPolicyInfo {
        private String id;
        private String json;
        private String ownerServiceId;
        private String ricId;
        private String typeId;
        private String statusNotificationUri;
        private boolean isTransient;
        private String lastModified;
    }

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String, Policy> policiesId = new HashMap<>();
    private MultiMap<Policy> policiesRic = new MultiMap<>();
    private MultiMap<Policy> policiesService = new MultiMap<>();
    private MultiMap<Policy> policiesType = new MultiMap<>();
    private final DataStore dataStore;

    private final ApplicationConfig appConfig;
    private static Gson gson = new GsonBuilder().create();

    public Policies(@Autowired ApplicationConfig appConfig) {
        this.appConfig = appConfig;
        this.dataStore = DataStore.create(appConfig, "policies");
    }

    public Flux<Policy> restoreFromDatabase(Ric ric, PolicyTypes types) {
        return dataStore.createDataStore() //
                .flatMapMany(x -> dataStore.listObjects(getPath(ric))) //
                .flatMap(dataStore::readObject) //
                .map(String::new) //
                .map(json -> gson.fromJson(json, PersistentPolicyInfo.class)) //
                .map(policyInfo -> toPolicy(policyInfo, ric, types)) //
                .doOnNext(this::put) //
                .filter(Objects::nonNull) //
                .doOnError(t -> logger.warn("Could not restore policy database for RIC: {}, reason : {}", ric.id(),
                        t.getMessage())) //
                .doFinally(sig -> logger.debug("Restored policy database for RIC: {}, number of policies: {}", ric.id(),
                        this.policiesRic.get(ric.id()).size())) //
                .onErrorResume(t -> Flux.empty()) //
        ;
    }

    public synchronized void put(Policy policy) {
        Policy previousDef = this.get(policy.getId());
        if (previousDef != null) {
            removeFromMaps(previousDef);
        }

        policiesId.put(policy.getId(), policy);
        policiesRic.put(policy.getRic().id(), policy.getId(), policy);
        policiesService.put(policy.getOwnerServiceId(), policy.getId(), policy);
        policiesType.put(policy.getType().getId(), policy.getId(), policy);
        if (!policy.isTransient()) {
            store(policy);
        }
    }

    public synchronized boolean containsPolicy(String id) {
        return policiesId.containsKey(id);
    }

    public synchronized Policy get(String id) {
        return policiesId.get(id);
    }

    public synchronized Policy getPolicy(String id) throws EntityNotFoundException {
        Policy p = policiesId.get(id);
        if (p == null) {
            throw new EntityNotFoundException("Could not find policy: " + id);
        }
        return p;
    }

    public synchronized Collection<Policy> getAll() {
        return new Vector<>(policiesId.values());
    }

    public synchronized Collection<Policy> getForService(String service) {
        return policiesService.get(service);
    }

    public synchronized Collection<Policy> getForRic(String ric) {
        return policiesRic.get(ric);
    }

    public synchronized Collection<Policy> getForType(String type) {
        return policiesType.get(type);
    }

    public synchronized Policy removeId(String id) {
        Policy p = policiesId.get(id);
        if (p != null) {
            remove(p);
        }
        return p;
    }

    public synchronized void remove(Policy policy) {
        if (!policy.isTransient()) {
            dataStore.deleteObject(getPath(policy)).subscribe();
        }
        removeFromMaps(policy);
    }

    public synchronized void removePoliciesForRic(String ricId) {
        Collection<Policy> policiesForRic = getForRic(ricId);
        for (Policy policy : policiesForRic) {
            remove(policy);
        }
    }

    public Collection<Policy> filterPolicies(@Nullable String typeId, @Nullable String ricId,
            @Nullable String serviceId, @Nullable String typeName) {

        if (typeId != null) {
            return filter(this.getForType(typeId), null, ricId, serviceId, typeName);
        } else if (serviceId != null) {
            return filter(this.getForService(serviceId), typeId, ricId, null, typeName);
        } else if (ricId != null) {
            return filter(this.getForRic(ricId), typeId, null, serviceId, typeName);
        } else {
            return filter(this.getAll(), typeId, ricId, serviceId, typeName);
        }
    }

    public synchronized int size() {
        return policiesId.size();
    }

    public synchronized void clear() {
        while (policiesId.size() > 0) {
            Set<String> keys = policiesId.keySet();
            removeId(keys.iterator().next());
        }
        dataStore.deleteAllObjects().onErrorResume(t -> Mono.empty()).subscribe();
    }

    private void store(Policy policy) {

        byte[] bytes = gson.toJson(toStorageObject(policy)).getBytes();
        this.dataStore.writeObject(this.getPath(policy), bytes) //
                .doOnError(t -> logger.error("Could not store job in S3, reason: {}", t.getMessage())) //
                .subscribe();
    }

    private void removeFromMaps(Policy policy) {
        policiesId.remove(policy.getId());
        policiesRic.remove(policy.getRic().id(), policy.getId());
        policiesService.remove(policy.getOwnerServiceId(), policy.getId());
        policiesType.remove(policy.getType().getId(), policy.getId());
    }

    private boolean isMatch(String filterValue, String actualValue) {
        return filterValue == null || actualValue.equals(filterValue);
    }

    private boolean isTypeMatch(Policy policy, @Nullable String typeName) {
        return (typeName == null) || policy.getType().getTypeId().getName().equals(typeName);
    }

    private Collection<Policy> filter(Collection<Policy> collection, String typeId, String ricId, String serviceId,
            String typeName) {
        if (typeId == null && ricId == null && serviceId == null && typeName == null) {
            return collection;
        }
        List<Policy> filtered = new ArrayList<>(collection.size());
        for (Policy p : collection) {
            if (isMatch(typeId, p.getType().getId()) && isMatch(ricId, p.getRic().id())
                    && isMatch(serviceId, p.getOwnerServiceId()) && isTypeMatch(p, typeName)) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private PersistentPolicyInfo toStorageObject(Policy p) {
        return PersistentPolicyInfo.builder() //
                .id(p.getId()) //
                .json(p.getJson()) //
                .ownerServiceId(p.getOwnerServiceId()) //
                .ricId(p.getRic().id()) //
                .statusNotificationUri(p.getStatusNotificationUri()) //
                .typeId(p.getType().getId()) //
                .isTransient(p.isTransient()) //
                .lastModified(p.getLastModified().toString()) //
                .build();
    }

    private Policy toPolicy(PersistentPolicyInfo p, Ric ric, PolicyTypes types) {
        try {
            return Policy.builder() //
                    .id(p.getId()) //
                    .isTransient(p.isTransient()) //
                    .json(p.getJson()) //
                    .lastModified(Instant.parse(p.lastModified)) //
                    .ownerServiceId(p.getOwnerServiceId()) //
                    .ric(ric) //
                    .statusNotificationUri(p.getStatusNotificationUri()) //
                    .type(types.getType(p.getTypeId())) //
                    .build();
        } catch (EntityNotFoundException e) {
            logger.warn("Not found: {}", e.getMessage());
            return null;
        }
    }

    private String getPath(Policy policy) {
        return getPath(policy.getRic()) + "/" + policy.getId() + ".json";
    }

    private String getPath(Ric ric) {
        return ric.id();
    }

}
