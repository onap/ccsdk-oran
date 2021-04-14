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

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import lombok.Builder;
import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;

@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
@Configuration
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

    private final ApplicationConfig appConfig;
    private static Gson gson = new GsonBuilder().create();

    public Policies(@Autowired ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    public synchronized void put(Policy policy) {
        policiesId.put(policy.getId(), policy);
        policiesRic.put(policy.getRic().id(), policy.getId(), policy);
        policiesService.put(policy.getOwnerServiceId(), policy.getId(), policy);
        policiesType.put(policy.getType().getId(), policy.getId(), policy);
        if (this.appConfig.getVardataDirectory() != null && !policy.isTransient()) {
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
            try {
                Files.delete(getPath(policy));
            } catch (Exception e) {
                logger.debug("Could not delete policy from database: {}", e.getMessage());
            }
        }
        policiesId.remove(policy.getId());
        policiesRic.remove(policy.getRic().id(), policy.getId());
        policiesService.remove(policy.getOwnerServiceId(), policy.getId());
        policiesType.remove(policy.getType().getId(), policy.getId());
    }

    public synchronized void removePoliciesForRic(String ricId) {
        Collection<Policy> policiesForRic = getForRic(ricId);
        for (Policy policy : policiesForRic) {
            remove(policy);
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
        try {
            if (this.appConfig.getVardataDirectory() != null) {
                FileSystemUtils.deleteRecursively(getDatabasePath());
            }
        } catch (Exception e) {
            logger.warn("Could not delete policy database : {}", e.getMessage());
        }
    }

    public void store(Policy policy) {
        try {
            Files.createDirectories(getDatabasePath(policy.getRic()));
            try (PrintStream out = new PrintStream(new FileOutputStream(getFile(policy)))) {
                out.print(gson.toJson(toStorageObject(policy)));
            }
        } catch (Exception e) {
            logger.warn("Could not store policy: {} {}", policy.getId(), e.getMessage());
        }
    }

    private File getFile(Policy policy) throws ServiceException {
        return getPath(policy).toFile();
    }

    private Path getPath(Policy policy) throws ServiceException {
        return Path.of(getDatabaseDirectory(policy.getRic()), policy.getId() + ".json");
    }

    public synchronized void restoreFromDatabase(Ric ric, PolicyTypes types) {
        try {
            Files.createDirectories(getDatabasePath(ric));
            for (File file : getDatabasePath(ric).toFile().listFiles()) {
                String json = Files.readString(file.toPath());
                PersistentPolicyInfo policyStorage = gson.fromJson(json, PersistentPolicyInfo.class);
                this.put(toPolicy(policyStorage, ric, types));
            }
            logger.debug("Restored policy database for RIC: {}, number of policies: {}", ric.id(),
                    this.policiesRic.get(ric.id()).size());
        } catch (Exception e) {
            logger.warn("Could not restore policy database for RIC: {}, reason : {}", ric.id(), e.getMessage());
        }
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

    Policy toPolicy(PersistentPolicyInfo p, Ric ric, PolicyTypes types) throws EntityNotFoundException {
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
    }

    private Path getDatabasePath(Ric ric) throws ServiceException {
        return Path.of(getDatabaseDirectory(ric));
    }

    private String getDatabaseDirectory(Ric ric) throws ServiceException {
        return getDatabaseDirectory() + "/" + ric.id();
    }

    private String getDatabaseDirectory() throws ServiceException {
        if (appConfig.getVardataDirectory() == null) {
            throw new ServiceException("No database storage provided");
        }
        return appConfig.getVardataDirectory() + "/database/policyInstances";
    }

    private Path getDatabasePath() throws ServiceException {
        return Path.of(getDatabaseDirectory());
    }
}
