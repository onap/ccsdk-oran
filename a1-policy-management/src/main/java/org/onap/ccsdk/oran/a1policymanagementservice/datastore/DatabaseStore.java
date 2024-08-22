/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.datastore;

import java.lang.invoke.MethodHandles;
import org.onap.ccsdk.oran.a1policymanagementservice.SpringContextProvider;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.BaseSchema;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.PoliciesRepository;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.PolicyTypesRepository;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.ServicesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DatabaseStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String OK = "OK";

    private final OperationTarget operationTarget;
    private final PoliciesRepository policiesRepository;
    private final ServicesRepository servicesRepository;
    private final PolicyTypesRepository policyTypesRepository;

    private enum OperationTarget {
        POLICYTYPES,
        SERVICES,
        POLICIES
    }

    public DatabaseStore(String target) {
        this.operationTarget = OperationTarget.valueOf(target.toUpperCase());
        this.policiesRepository = SpringContextProvider.getSpringContext().getBean(PoliciesRepository.class);
        this.servicesRepository = SpringContextProvider.getSpringContext().getBean(ServicesRepository.class);
        this.policyTypesRepository = SpringContextProvider.getSpringContext().getBean(PolicyTypesRepository.class);
    }

    @Override
    public Flux<String> listObjects(String prefix) {
        logger.debug("Listing objects for prefix {} and target {}", prefix, operationTarget.name());
        return Flux.just(operationTarget).flatMap(localOperationTarget -> {
            if (localOperationTarget == OperationTarget.POLICIES) {
                return policiesRepository.findByIdStartingWith(prefix).map(BaseSchema::getId);
            } else if (localOperationTarget == OperationTarget.POLICYTYPES) {
                return policyTypesRepository.findAll().map(BaseSchema::getId);
            } else {
                return servicesRepository.findAll().map(BaseSchema::getId);
            }
        });
    }

    @Override
    public Mono<byte[]> readObject(String name) {
        logger.debug("Reading object {} for target {}", name, operationTarget.name());
        return Mono.just(operationTarget).flatMap(localOperationTarget -> {
            if (localOperationTarget == OperationTarget.POLICIES) {
                return policiesRepository.findById(name).map(policy -> policy.getPayload().getBytes());
            } else if (localOperationTarget == OperationTarget.POLICYTYPES) {
                return policyTypesRepository.findById(name).map(policyType -> policyType.getPayload().getBytes());
            } else {
                return servicesRepository.findById(name).map(service -> service.getPayload().getBytes());
            }
        });
    }

    @Override
    public Mono<byte[]> writeObject(String name, byte[] fileData) {
        logger.debug("Writing object {} for target {}", name, operationTarget.name());
        return Mono.just(operationTarget).flatMap(localOperationTarget -> {
            if (localOperationTarget == OperationTarget.POLICIES) {
                return policiesRepository.findById(name).map(policy -> Boolean.FALSE).defaultIfEmpty(Boolean.TRUE)
                        .flatMap(isNewPolicy -> {
                            Policy policy = new Policy(name, new String(fileData));
                            policy.setNew(isNewPolicy);
                            return policiesRepository.save(policy).map(savedPolicy -> fileData);
                        });
            } else if (localOperationTarget == OperationTarget.POLICYTYPES) {
                return policyTypesRepository.findById(name).map(policyType -> Boolean.FALSE).defaultIfEmpty(Boolean.TRUE)
                        .flatMap(isNewPolicyType -> {
                            PolicyType policyType = new PolicyType(name, new String(fileData));
                            policyType.setNew(isNewPolicyType);
                            return policyTypesRepository.save(policyType).map(savedPolicyType -> fileData);
                        });
            } else {
                return servicesRepository.findById(name).map(service -> Boolean.FALSE).defaultIfEmpty(Boolean.TRUE)
                        .flatMap(isNewService -> {
                            Service service = new Service(name, new String(fileData));
                            service.setNew(isNewService);
                            return servicesRepository.save(service).map(savedService -> fileData);
                        });
            }
        });
    }

    @Override
    public Mono<Boolean> deleteObject(String name) {
        logger.debug("Deleting object {} for target {}", name, operationTarget.name());
        return Mono.just(operationTarget).flatMap(localOperationTarget -> {
            if (localOperationTarget == OperationTarget.POLICIES) {
                return policiesRepository.deleteById(name).thenReturn(Boolean.TRUE);
            } else if (localOperationTarget == OperationTarget.POLICYTYPES) {
                return policyTypesRepository.deleteById(name).thenReturn(Boolean.TRUE);
            } else {
                return servicesRepository.deleteById(name).thenReturn(Boolean.TRUE);
            }
        });
    }

    @Override
    public Mono<String> createDataStore() {
        return Mono.just(OK);
    }

    @Override
    public Mono<String> deleteAllObjects() {
        logger.debug("Deleting All objects for target {}", operationTarget.name());
        return Mono.just(operationTarget).flatMap(localOperationTarget -> {
            if (localOperationTarget == OperationTarget.POLICIES) {
                return policiesRepository.deleteAll().thenReturn(OK);
            } else if (localOperationTarget == OperationTarget.POLICYTYPES) {
                return policyTypesRepository.deleteAll().thenReturn(OK);
            } else {
                return servicesRepository.deleteAll().thenReturn(OK);
            }
        });
    }
}
