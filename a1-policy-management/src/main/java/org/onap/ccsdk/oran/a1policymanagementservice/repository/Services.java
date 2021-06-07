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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FileSystemUtils;

public class Services {
    private static final Logger logger = LoggerFactory.getLogger(Services.class);
    private static Gson gson = Service.createGson();
    private final ApplicationConfig appConfig;

    private Map<String, Service> registeredServices = new HashMap<>();

    public Services(@Autowired ApplicationConfig appConfig) {
        this.appConfig = appConfig;
        restoreFromDatabase();
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
            try {
                Files.delete(getPath(service));
            } catch (Exception e) {
                // Doesn't matter.
            }
        }
    }

    public synchronized int size() {
        return registeredServices.size();
    }

    public synchronized void clear() {
        registeredServices.clear();
        try {
            FileSystemUtils.deleteRecursively(getDatabasePath());
        } catch (Exception e) {
            logger.warn("Could not delete services database : {}", e.getMessage());
        }
    }

    public void store(Service service) {
        try {
            Files.createDirectories(getDatabasePath());
            try (PrintStream out = new PrintStream(new FileOutputStream(getFile(service)))) {
                String str = gson.toJson(service);
                out.print(str);
            }
        } catch (ServiceException e) {
            logger.debug("Could not store service: {} {}", service.getName(), e.getMessage());
        } catch (IOException e) {
            logger.warn("Could not store pservice: {} {}", service.getName(), e.getMessage());
        }
    }

    private File getFile(Service service) throws ServiceException {
        return getPath(service).toFile();
    }

    private Path getPath(Service service) throws ServiceException {
        return Path.of(getDatabaseDirectory(), service.getName() + ".json");
    }

    void restoreFromDatabase() {
        try {
            Files.createDirectories(getDatabasePath());
            for (File file : getDatabasePath().toFile().listFiles()) {
                String json = Files.readString(file.toPath());
                Service service = gson.fromJson(json, Service.class);
                this.registeredServices.put(service.getName(), service);
            }
            logger.debug("Restored type database,no of services: {}", this.registeredServices.size());
        } catch (ServiceException e) {
            logger.debug("Could not restore services database : {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Could not restore services database : {}", e.getMessage());
        }
    }

    private String getDatabaseDirectory() throws ServiceException {
        if (appConfig.getVardataDirectory() == null) {
            throw new ServiceException("No storage provided");
        }
        return appConfig.getVardataDirectory() + "/database/services";
    }

    private Path getDatabasePath() throws ServiceException {
        return Path.of(getDatabaseDirectory());
    }
}
