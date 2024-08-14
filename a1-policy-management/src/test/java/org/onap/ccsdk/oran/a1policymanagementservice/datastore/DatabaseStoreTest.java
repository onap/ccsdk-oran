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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.SpringContextProvider;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.BaseSchema;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.database.entities.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.PoliciesRepository;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.PolicyTypesRepository;
import org.onap.ccsdk.oran.a1policymanagementservice.database.repositories.ServicesRepository;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class DatabaseStoreTest {

    public static final String OK = "OK";
    @Mock
    PoliciesRepository policiesRepository;
    @Mock
    ServicesRepository servicesRepository;
    @Mock
    PolicyTypesRepository policyTypesRepository;
    @Mock
    ApplicationContext applicationContext;
    @InjectMocks
    SpringContextProvider springContextProvider;

    private enum OperationTarget {
        POLICYTYPES("policytypes"),
        SERVICES("services"),
        POLICIES("policies");

        final String label;

        OperationTarget(String label) {
            this.label = label;
        }
    }

    @BeforeEach
    void initialize() {
        when(applicationContext.getBean(PoliciesRepository.class)).thenReturn(policiesRepository);
        when(applicationContext.getBean(PolicyTypesRepository.class)).thenReturn(policyTypesRepository);
        when(applicationContext.getBean(ServicesRepository.class)).thenReturn(servicesRepository);
        springContextProvider.setApplicationContext(applicationContext);
    }

    @Test
    void testCreateDataStore() {
        DatabaseStore databaseStore = new DatabaseStore(OperationTarget.POLICYTYPES.name());
        StepVerifier.create(databaseStore.createDataStore()).expectNext(OK).verifyComplete();
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testListObjectsSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            String prefix = "ric1";
            Policy policy1 = new Policy(prefix + "/listpolicy1.json", "{}");
            Policy policy2 = new Policy(prefix + "/listpolicy2.json", "{}");
            when(policiesRepository.findByIdStartingWith(any())).thenReturn(Flux.just(policy1, policy2));
            StepVerifier.create(databaseStore.listObjects(prefix)).expectNext(policy1.getId()).expectNext(policy2.getId())
                    .verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            PolicyType policyType1 = new PolicyType("/listpolicytype1.json", "{}");
            PolicyType policyType2 = new PolicyType("/listpolicytype2.json", "{}");
            when(policyTypesRepository.findAll()).thenReturn(Flux.just(policyType1, policyType2));
            StepVerifier.create(databaseStore.listObjects("")).expectNext(policyType1.getId())
                    .expectNext(policyType2.getId()).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            Service service1 = new Service("/listservice1.json", "{}");
            Service service2 = new Service("/listservice2.json", "{}");
            when(servicesRepository.findAll()).thenReturn(Flux.just(service1, service2));
            StepVerifier.create(databaseStore.listObjects("")).expectNext(service1.getId()).expectNext(service2.getId())
                    .verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testListObjectsFailure(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        String errorMessage = "Unable to list the objects of type " + operationTarget.name();
        if (operationTarget == OperationTarget.POLICIES) {
            String prefix = "ric1";
            when(policiesRepository.findByIdStartingWith(any())).thenReturn(Flux.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.listObjects(prefix)).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            when(policyTypesRepository.findAll()).thenReturn(Flux.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.listObjects("")).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.SERVICES) {
            when(servicesRepository.findAll()).thenReturn(Flux.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.listObjects("")).expectErrorMessage(errorMessage).verify();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testReadObjectSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/readpolicy1.json";
            String policyPayload = "{\"name\":\"readpolicy1\"}";
            Policy policy1 = new Policy(policyName, policyPayload);
            when(policiesRepository.findById(anyString())).thenReturn(Mono.just(policy1));
            StepVerifier.create(databaseStore.readObject(policyName)).consumeNextWith(bytes -> {
                assertArrayEquals(bytes, policyPayload.getBytes());
            }).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "readpolicytype1.json";
            String policyTypePayload = "{\"name\":\"readpolicytype1\"}";
            PolicyType policyType1 = new PolicyType(policyTypeName, policyTypePayload);
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.just(policyType1));
            StepVerifier.create(databaseStore.readObject(policyTypeName)).consumeNextWith(bytes -> {
                assertArrayEquals(bytes, policyTypePayload.getBytes());
            }).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "readservice1.json";
            String servicePayload = "{\"name\":\"readservice1\"}";
            Service service1 = new Service(serviceName, servicePayload);
            when(servicesRepository.findById(anyString())).thenReturn(Mono.just(service1));
            StepVerifier.create(databaseStore.readObject(serviceName)).consumeNextWith(bytes -> {
                assertArrayEquals(bytes, servicePayload.getBytes());
            }).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testReadObjectFailure(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        String errorMessage = "Unable to read the objects of type " + operationTarget.name();
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/readpolicy1.json";
            when(policiesRepository.findById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.readObject(policyName)).expectErrorMessage(errorMessage).verify();
            when(policiesRepository.findById(anyString())).thenReturn(Mono.empty());
            StepVerifier.create(databaseStore.readObject(policyName)).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "readpolicytype1.json";
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.readObject(policyTypeName)).expectErrorMessage(errorMessage).verify();
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.empty());
            StepVerifier.create(databaseStore.readObject(policyTypeName)).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "readservice1.json";
            when(servicesRepository.findById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.readObject(serviceName)).expectErrorMessage(errorMessage).verify();
            when(servicesRepository.findById(anyString())).thenReturn(Mono.empty());
            StepVerifier.create(databaseStore.readObject(serviceName)).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testWriteObjectInsertSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/writeinserpolicy1.json";
            String policyPayload = "{\"name\":\"writeinserpolicy1\"}";
            Policy policy1 = new Policy(policyName, policyPayload);
            when(policiesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(policiesRepository.save(any(Policy.class))).thenReturn(Mono.just(policy1));
            StepVerifier.create(databaseStore.writeObject(policyName, policyPayload.getBytes())).consumeNextWith(bytes -> {
                assertArrayEquals(bytes, policyPayload.getBytes());
                verify(policiesRepository).save(argThat(BaseSchema::isNew));
            }).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "writeinsertpolicytype1.json";
            String policyTypePayload = "{\"name\":\"writeinsertpolicytype1\"}";
            PolicyType policyType1 = new PolicyType(policyTypeName, policyTypePayload);
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(policyTypesRepository.save(any(PolicyType.class))).thenReturn(Mono.just(policyType1));
            StepVerifier.create(databaseStore.writeObject(policyTypeName, policyTypePayload.getBytes()))
                    .consumeNextWith(bytes -> {
                        assertArrayEquals(bytes, policyTypePayload.getBytes());
                        verify(policyTypesRepository).save(argThat(BaseSchema::isNew));
                    }).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "writeinsertservice1.json";
            String servicePayload = "{\"name\":\"writeinsertservice1\"}";
            Service service1 = new Service(serviceName, servicePayload);
            when(servicesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(servicesRepository.save(any(Service.class))).thenReturn(Mono.just(service1));
            StepVerifier.create(databaseStore.writeObject(serviceName, servicePayload.getBytes()))
                    .consumeNextWith(bytes -> {
                        assertArrayEquals(bytes, servicePayload.getBytes());
                        verify(servicesRepository).save(argThat(BaseSchema::isNew));
                    }).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testWriteObjectUpdateSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/writeupdatepolicy1.json";
            String policyPayload = "{\"name\":\"writeupdatepolicy1\"}";
            Policy policy1 = new Policy(policyName, policyPayload);
            when(policiesRepository.findById(anyString())).thenReturn(Mono.just(policy1));
            when(policiesRepository.save(any(Policy.class))).thenReturn(Mono.just(policy1));
            StepVerifier.create(databaseStore.writeObject(policyName, policyPayload.getBytes())).consumeNextWith(bytes -> {
                assertArrayEquals(bytes, policyPayload.getBytes());
                verify(policiesRepository).save(argThat(policy -> !policy.isNew()));
            }).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "writeupdatepolicytype1.json";
            String policyTypePayload = "{\"name\":\"writeupdatepolicytype1\"}";
            PolicyType policyType1 = new PolicyType(policyTypeName, policyTypePayload);
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.just(policyType1));
            when(policyTypesRepository.save(any(PolicyType.class))).thenReturn(Mono.just(policyType1));
            StepVerifier.create(databaseStore.writeObject(policyTypeName, policyTypePayload.getBytes()))
                    .consumeNextWith(bytes -> {
                        assertArrayEquals(bytes, policyTypePayload.getBytes());
                        verify(policyTypesRepository).save(argThat(policy -> !policy.isNew()));
                    }).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "writeupdateservice1.json";
            String servicePayload = "{\"name\":\"writeupdateservice1\"}";
            Service service1 = new Service(serviceName, servicePayload);
            when(servicesRepository.findById(anyString())).thenReturn(Mono.just(service1));
            when(servicesRepository.save(any(Service.class))).thenReturn(Mono.just(service1));
            StepVerifier.create(databaseStore.writeObject(serviceName, servicePayload.getBytes()))
                    .consumeNextWith(bytes -> {
                        assertArrayEquals(bytes, servicePayload.getBytes());
                        verify(servicesRepository).save(argThat(policy -> !policy.isNew()));
                    }).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testWriteObjectFailure(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        String errorMessage = "Unable to write the objects of type " + operationTarget.name();
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/writepolicy1.json";
            String policyPayload = "{\"name\":\"writepolicy1\"}";
            when(policiesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(policiesRepository.save(any(Policy.class))).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.writeObject(policyName, policyPayload.getBytes()))
                    .expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "writepolicytype1.json";
            String policyTypePayload = "{\"name\":\"writepolicytype1\"}";
            when(policyTypesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(policyTypesRepository.save(any(PolicyType.class))).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.writeObject(policyTypeName, policyTypePayload.getBytes()))
                    .expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "writeservice1.json";
            String servicePayload = "{\"name\":\"writeservice1\"}";
            when(servicesRepository.findById(anyString())).thenReturn(Mono.empty());
            when(servicesRepository.save(any(Service.class))).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.writeObject(serviceName, servicePayload.getBytes()))
                    .expectErrorMessage(errorMessage).verify();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testDeleteObjectSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/deletepolicy1.json";
            when(policiesRepository.deleteById(anyString())).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteObject(policyName)).expectNext(true).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "deletepolicytype1.json";
            when(policyTypesRepository.deleteById(anyString())).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteObject(policyTypeName)).expectNext(true).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "deleteservice1.json";
            when(servicesRepository.deleteById(anyString())).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteObject(serviceName)).expectNext(true).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testDeleteObjectFailure(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        String errorMessage = "Unable to delete the objects of type " + operationTarget.name();
        if (operationTarget == OperationTarget.POLICIES) {
            String policyName = "ric1/deletepolicy1.json";
            when(policiesRepository.deleteById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteObject(policyName)).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            String policyTypeName = "deletepolicytype1.json";
            when(policyTypesRepository.deleteById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteObject(policyTypeName)).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.SERVICES) {
            String serviceName = "deleteservice1.json";
            when(servicesRepository.deleteById(anyString())).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteObject(serviceName)).expectErrorMessage(errorMessage).verify();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testDeleteAllObjectSuccess(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        if (operationTarget == OperationTarget.POLICIES) {
            when(policiesRepository.deleteAll()).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteAllObjects()).expectNext(OK).verifyComplete();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            when(policyTypesRepository.deleteAll()).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteAllObjects()).expectNext(OK).verifyComplete();
        } else if (operationTarget == OperationTarget.SERVICES) {
            when(servicesRepository.deleteAll()).thenReturn(Mono.just("").then());
            StepVerifier.create(databaseStore.deleteAllObjects()).expectNext(OK).verifyComplete();
        }
    }

    @ParameterizedTest
    @EnumSource(OperationTarget.class)
    void testDeleteAllObjectFailure(OperationTarget operationTarget) {
        DatabaseStore databaseStore = new DatabaseStore(operationTarget.name());
        String errorMessage = "Unable to delete all the objects of type " + operationTarget.name();
        if (operationTarget == OperationTarget.POLICIES) {
            when(policiesRepository.deleteAll()).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteAllObjects()).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.POLICYTYPES) {
            when(policyTypesRepository.deleteAll()).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteAllObjects()).expectErrorMessage(errorMessage).verify();
        } else if (operationTarget == OperationTarget.SERVICES) {
            when(servicesRepository.deleteAll()).thenReturn(Mono.error(new Throwable(errorMessage)));
            StepVerifier.create(databaseStore.deleteAllObjects()).expectErrorMessage(errorMessage).verify();
        }
    }

}