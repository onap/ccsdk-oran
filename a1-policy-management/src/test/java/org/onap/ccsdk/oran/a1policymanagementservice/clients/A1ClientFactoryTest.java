/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class A1ClientFactoryTest {
    private static final String RIC_NAME = "Name";
    private static final String EXCEPTION_MESSAGE = "Error";

    @Mock
    private ApplicationConfig applicationConfigMock;

    @Mock
    A1Client clientMock1;

    @Mock
    A1Client clientMock2;

    @Mock
    A1Client clientMock3;

    @Mock
    A1Client clientMock4;

    private Ric ric;
    private A1ClientFactory factoryUnderTest;

    private static RicConfig ricConfig(String controllerName, String customAdapter) {
        ControllerConfig controllerConfig = null;
        if (!controllerName.isEmpty()) {
            controllerConfig = ControllerConfig.builder().baseUrl("baseUrl").name(controllerName).build();
        }
        return RicConfig.builder() //
                .ricId(RIC_NAME) //
                .baseUrl("baseUrl") //
                .controllerConfig(controllerConfig) //
                .customAdapterClass(customAdapter) //
                .build();
    }

    private static RicConfig ricConfig(String controllerName) {
        return ricConfig(controllerName, "");
    }

    @BeforeEach
    void createFactoryUnderTest() {
        SecurityContext sec = new SecurityContext("");
        factoryUnderTest = spy(new A1ClientFactory(applicationConfigMock, sec));
        this.ric = new Ric(ricConfig(""));
    }

    @Test
    @DisplayName("test get Protocol Version ok")
    void getProtocolVersion_ok() throws ServiceException {
        whenGetProtocolVersionThrowException(clientMock1);
        whenGetProtocolVersionReturn(clientMock2, A1ProtocolType.STD_V1_1);
        doReturn(clientMock1, clientMock2).when(factoryUnderTest).createClient(any(), any());

        A1Client client = factoryUnderTest.createA1Client(ric).block();

        assertEquals(clientMock2, client, "Not correct client returned");
        assertEquals(A1ProtocolType.STD_V1_1, ric.getProtocolVersion(), "Not correct protocol");
    }

    @Test
    @DisplayName("test get Protocol Version ok Last")
    void getProtocolVersion_ok_Last() throws ServiceException {
        whenGetProtocolVersionThrowException(clientMock1, clientMock2, clientMock3);
        whenGetProtocolVersionReturn(clientMock4, A1ProtocolType.STD_V1_1);
        doReturn(clientMock1, clientMock2, clientMock3, clientMock4).when(factoryUnderTest).createClient(any(), any());

        A1Client client = factoryUnderTest.createA1Client(ric).block();

        assertEquals(clientMock4, client, "Not correct client returned");
        assertEquals(A1ProtocolType.STD_V1_1, ric.getProtocolVersion(), "Not correct protocol");
    }

    public static class CustomA1AdapterFactory implements A1Client.Factory {
        @Override
        public A1Client create(RicConfig ricConfig, AsyncRestClientFactory restClientFactory) {
            return new StdA1ClientVersion2(ricConfig, restClientFactory);
        }
    }

    @Test
    @DisplayName("test Custom Adapter Creation")
    void testCustomAdapterCreation() {

        Ric ricLocal = new Ric(ricConfig("", CustomA1AdapterFactory.class.getName()));
        A1Client client = factoryUnderTest.createA1Client(ricLocal).block();
        assertNotNull(client);
        assertEquals(client.getClass(), StdA1ClientVersion2.class);

        ricLocal = new Ric(ricConfig("", "org.onap.ccsdk.oran.a1policymanagementservice.clients.StdA1ClientVersion2"));
        client = factoryUnderTest.createA1Client(ricLocal).block();
        assertNotNull(client);
        assertEquals(client.getClass(), StdA1ClientVersion2.class);

        ricLocal = new Ric(
                ricConfig("", "org.onap.ccsdk.oran.a1policymanagementservice.clients.StdA1ClientVersion2$Factory"));
        client = factoryUnderTest.createA1Client(ricLocal).block();
        assertNotNull(client);
        assertEquals(client.getClass(), StdA1ClientVersion2.class);

        Exception e = Assertions.assertThrows(Exception.class, () -> {
            factoryUnderTest.createClient(new Ric(ricConfig("", "junk")), A1ProtocolType.CUSTOM_PROTOCOL);
        });
        assertEquals("Could not find class: junk", e.getMessage());
    }

    @Test
    @DisplayName("test get Protocol Version error")
    void getProtocolVersion_error() throws ServiceException {
        whenGetProtocolVersionThrowException(clientMock1, clientMock2, clientMock3, clientMock4);
        doReturn(clientMock1, clientMock2, clientMock3, clientMock4).when(factoryUnderTest).createClient(any(), any());

        StepVerifier.create(factoryUnderTest.createA1Client(ric)) //
                .expectSubscription() //
                .expectError() //
                .verify();

        assertEquals(A1ProtocolType.UNKNOWN, ric.getProtocolVersion(), "Protocol negotiation failed for " + ric.id());
    }

    private A1Client createClient(A1ProtocolType version) throws ServiceException {
        return factoryUnderTest.createClient(ric, version);
    }

    @Test
    @DisplayName("tes create check types")
    void create_check_types() throws ServiceException {
        assertTrue(createClient(A1ProtocolType.STD_V1_1) instanceof StdA1ClientVersion1);
        assertTrue(createClient(A1ProtocolType.OSC_V1) instanceof OscA1Client);
    }

    @Test
    @DisplayName("test create check types controllers")
    void create_check_types_controllers() throws ServiceException {
        this.ric = new Ric(ricConfig("anythingButEmpty"));

        assertTrue(createClient(A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1) instanceof CcsdkA1AdapterClient);

        assertTrue(createClient(A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1) instanceof CcsdkA1AdapterClient);
    }

    private void whenGetProtocolVersionThrowException(A1Client... clientMocks) {
        for (A1Client clientMock : clientMocks) {
            when(clientMock.getProtocolVersion()).thenReturn(Mono.error(new Exception(EXCEPTION_MESSAGE)));
        }
    }

    private void whenGetProtocolVersionReturn(A1Client clientMock, A1ProtocolType protocol) {
        when(clientMock.getProtocolVersion()).thenReturn(Mono.just(protocol));
    }

}
