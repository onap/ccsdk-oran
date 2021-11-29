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
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.jetbrains.annotations.Nullable;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;

@Configuration
public class PolicyTypes {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String, PolicyType> types = new HashMap<>();
    private final ApplicationConfig appConfig;
    private static Gson gson = new GsonBuilder().create();

    public PolicyTypes(@Autowired ApplicationConfig appConfig) {
        this.appConfig = appConfig;
        restoreFromDatabase();
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
     *        version string (major.minor.patch)
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
        try {
            FileSystemUtils.deleteRecursively(getDatabasePath());
        } catch (IOException | ServiceException e) {
            logger.warn("Could not delete policy type database : {}", e.getMessage());
        }
    }

    public void store(PolicyType type) {
        try {
            Files.createDirectories(getDatabasePath());
            try (PrintStream out = new PrintStream(new FileOutputStream(getFile(type)))) {
                out.print(gson.toJson(type));
            }
        } catch (ServiceException e) {
            logger.debug("Could not store policy type: {} {}", type.getId(), e.getMessage());
        } catch (IOException e) {
            logger.warn("Could not store policy type: {} {}", type.getId(), e.getMessage());
        }
    }

    private File getFile(PolicyType type) throws ServiceException {
        return Path.of(getDatabaseDirectory(), type.getId() + ".json").toFile();
    }

    void restoreFromDatabase() {
        try {
            Files.createDirectories(getDatabasePath());
            for (File file : getDatabasePath().toFile().listFiles()) {
                String json = Files.readString(file.toPath());
                PolicyType type = gson.fromJson(json, PolicyType.class);
                this.types.put(type.getId(), type);
            }
            logger.debug("Restored type database,no of types: {}", this.types.size());
        } catch (ServiceException e) {
            logger.debug("Could not restore policy type database : {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Could not restore policy type database : {}", e.getMessage());
        }
    }

    private String getDatabaseDirectory() throws ServiceException {
        if (appConfig.getVardataDirectory() == null) {
            throw new ServiceException("No policy type storage provided");
        }
        return appConfig.getVardataDirectory() + "/database/policyTypes";
    }

    private Path getDatabasePath() throws ServiceException {
        return Path.of(getDatabaseDirectory());
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

    private static boolean isTypeCompatibleWithVersion(PolicyType type, PolicyType.Version version) {
        try {
            PolicyType.TypeId typeId = type.getTypeId();
            PolicyType.Version typeVersion = PolicyType.Version.ofString(typeId.getVersion());
            return (typeVersion.major == version.major && typeVersion.minor >= version.minor);
        } catch (Exception e) {
            logger.warn("Ignoring type with syntactically incorrect type ID: {}", type.getId());
            return false;
        }
    }

    private static Collection<PolicyType> filterCompatibleWithVersion(Collection<PolicyType> types, String versionStr)
            throws ServiceException {
        Collection<PolicyType> result = new ArrayList<>();
        PolicyType.Version otherVersion = PolicyType.Version.ofString(versionStr);
        for (PolicyType type : types) {
            if (isTypeCompatibleWithVersion(type, otherVersion)) {
                result.add(type);
            }
        }
        return result;
    }

}
