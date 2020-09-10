/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 Nordix Foundation.
 * ================================================================================
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.ccsdk.features.a1.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutput;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class A1AdapterProviderTest {

    private static final String RESPONSE_CODE = "response-code";
    private static final String OK_RESPONSE = "200";

    protected static final Logger LOG = LoggerFactory.getLogger(A1AdapterProviderTest.class);

    private A1AdapterProvider a1AdapterProviderMock = null;
    @Mock
    private NotificationPublishService mockNotificationPublishService;
    @Mock
    private RpcProviderRegistry mockRpcProviderRegistry;
    @Mock
    private A1AdapterClient a1AdapterClient;
    private static String module = "A1-ADAPTER-API";
    private static String mode = "sync";

    @BeforeEach
    void setUp() {
        a1AdapterProviderMock = Mockito.spy(new A1AdapterProvider(mockNotificationPublishService,
            mockRpcProviderRegistry, a1AdapterClient));
    }

    @Test
    void deleteA1PolicyType() throws Exception {
        String rpc = "deleteA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty(RESPONSE_CODE, OK_RESPONSE);
        DeleteA1PolicyInputBuilder inputBuilder = new DeleteA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(
            a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Builder.class), any(Properties.class)))
                .thenReturn(respProps);
        ListenableFuture<RpcResult<DeleteA1PolicyOutput>> result =
            a1AdapterProviderMock.deleteA1Policy(inputBuilder.build());
        assertEquals(OK_RESPONSE, String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    void getA1Policy() throws Exception {
        String rpc = "getA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty(RESPONSE_CODE, OK_RESPONSE);
        GetA1PolicyInputBuilder inputBuilder = new GetA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(
            a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Builder.class), any(Properties.class)))
                .thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyOutput>> result = a1AdapterProviderMock.getA1Policy(inputBuilder.build());
        assertEquals(OK_RESPONSE, String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    void getA1PolicyType() throws Exception {
        String rpc = "getA1PolicyType";
        Properties respProps = new Properties();
        respProps.setProperty(RESPONSE_CODE, OK_RESPONSE);
        GetA1PolicyTypeInputBuilder inputBuilder = new GetA1PolicyTypeInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(
            a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Builder.class), any(Properties.class)))
                .thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyTypeOutput>> result =
            a1AdapterProviderMock.getA1PolicyType(inputBuilder.build());
        assertEquals(OK_RESPONSE, String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    void getA1PolicyStatus() throws Exception {
        String rpc = "getA1PolicyStatus";
        Properties respProps = new Properties();
        respProps.setProperty(RESPONSE_CODE, OK_RESPONSE);
        GetA1PolicyStatusInputBuilder inputBuilder = new GetA1PolicyStatusInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(
            a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Builder.class), any(Properties.class)))
                .thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyStatusOutput>> result =
            a1AdapterProviderMock.getA1PolicyStatus(inputBuilder.build());
        assertEquals(OK_RESPONSE, String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    void putA1Policy() throws Exception {
        String rpc = "putA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty(RESPONSE_CODE, OK_RESPONSE);
        PutA1PolicyInputBuilder inputBuilder = new PutA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(
            a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Builder.class), any(Properties.class)))
                .thenReturn(respProps);
        ListenableFuture<RpcResult<PutA1PolicyOutput>> result = a1AdapterProviderMock.putA1Policy(inputBuilder.build());
        assertEquals(OK_RESPONSE, String.valueOf(result.get().getResult().getHttpStatus()));
    }
}
