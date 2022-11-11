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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.datastore.DataStore;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PolicyTypes {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String, PolicyType> types = new HashMap<>();
    private static Gson gson = new GsonBuilder().create();
    private final DataStore dataStore;

    public PolicyTypes(@Autowired ApplicationConfig appConfig) {
        this.dataStore = DataStore.create(appConfig, "policytypes");
    }

    public synchronized PolicyType getType(String name) throws EntityNotFoundException {
        PolicyType t = types.get(name);
        if (t == null) {
            throw new EntityNotFoundException("Could not find type: " + name);
        }
        return t;
    }

    public synchronized PolicyType get(String name) {
        return types.get(name);
    }

    public synchronized void put(PolicyType type) {
        types.put(type.getId(), type);
        store(type);
    }

    public synchronized boolean contains(String policyType) {
        return types.containsKey(policyType);
    }

    public synchronized Collection<PolicyType> getAll() {
        return new Vector<>(types.values());
    }

    /**
     * Filter out types matching criterias
     *
     * @param types the types to select from
     * @param typeName select types with given type name
     * @param compatibleWithVersion select types that are compatible with given
     *        version string (major.minor.patch).
     *        Matching types will be sorted in ascending
     *        order.
     * @return the types that matches given criterias
     * @throws ServiceException if there are errors in the given input
     */
    public static Collection<PolicyType> filterTypes(Collection<PolicyType> types, @Nullable String typeName,
            @Nullable String compatibleWithVersion) throws ServiceException {
        if (typeName != null) {
            types = filterTypeName(types, typeName);
        }
        if (compatibleWithVersion != null) {
            types = filterCompatibleWithVersion(types, compatibleWithVersion);
        }
        return types;
    }

    public synchronized int size() {
        return types.size();
    }

    public synchronized void clear() {
        this.types.clear();
        dataStore.deleteAllObjects().onErrorResume(t -> Mono.empty()).subscribe();
    }

    public void store(PolicyType type) {
        byte[] bytes = gson.toJson(type).getBytes();
        dataStore.writeObject(getPath(type), bytes) //
                .doOnError(t -> logger.warn("Could not store policy type: {} {}", type.getId(), t.getMessage()))
                .subscribe();
    }

    public Flux<PolicyType> restoreFromDatabase() {

        return this.dataStore.createDataStore().flatMapMany(x -> dataStore.listObjects("")) //
                .flatMap(dataStore::readObject) //
                .map(String::new) //
                .map(json -> gson.fromJson(json, PolicyType.class)).doOnNext(type -> this.types.put(type.getId(), type)) //
                .doOnError(t -> logger.warn("Could not restore policy type database : {}", t.getMessage())) //
                .doFinally(sig -> logger.debug("Restored type database,no of types: {}", this.types.size()))
                .onErrorResume(t -> Flux.empty()); //

    }

    private static Collection<PolicyType> filterTypeName(Collection<PolicyType> types, String typeName) {
        Collection<PolicyType> result = new ArrayList<>();
        for (PolicyType type : types) {
            PolicyType.TypeId nameVersion = type.getTypeId();
            if (nameVersion.getName().equals(typeName)) {
                result.add(type);
            }
        }
        return result;
    }

    private static Collection<PolicyType> filterCompatibleWithVersion(Collection<PolicyType> types, String versionStr)
            throws ServiceException {
        List<PolicyType> result = new ArrayList<>();
        PolicyType.Version requestedVersion = PolicyType.Version.ofString(versionStr);
        for (PolicyType type : types) {
            if (type.getVersion().isCompatibleWith(requestedVersion)) {
                result.add(type);
            }
        }
        result.sort((left, right) -> left.getVersion().compareTo(right.getVersion()));
        return result;
    }

    private String getPath(PolicyType type) {
        return type.getId() + ".json";
    }

}
