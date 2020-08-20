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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.onap.ccsdk.sli.core.sli.SvcLogicException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class Tests all the methods in A1AdapterProvider
 *
 */

@RunWith(MockitoJUnitRunner.Silent.class)
public class A1AdapterProviderTest {

    protected static final Logger LOG = LoggerFactory.getLogger(A1AdapterProviderTest.class);

    class A1AdapterProviderMock extends A1AdapterProvider {

        A1AdapterProviderMock(final DataBroker dataBroker, final NotificationPublishService notificationPublishService,
                final RpcProviderRegistry rpcProviderRegistry, final A1AdapterClient A1AdapterClient) {
            super(dataBroker, mockNotificationPublishService, mockRpcProviderRegistry, a1AdapterClient);
        }

    }

    private A1AdapterProviderMock a1AdapterProviderMock = null;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private NotificationPublishService mockNotificationPublishService;
    @Mock
    private RpcProviderRegistry mockRpcProviderRegistry;
    @Mock
    private A1AdapterClient a1AdapterClient;
    private static String module = "A1-ADAPTER-API";
    private static String mode = "sync";

    @Before
    public void setUp() throws Exception {

        a1AdapterProviderMock = new A1AdapterProviderMock(dataBroker, mockNotificationPublishService,
                mockRpcProviderRegistry, a1AdapterClient);
        a1AdapterProviderMock = Mockito.spy(a1AdapterProviderMock);

    }

    @Test
    public void test_deleteA1PolicyType() throws SvcLogicException, InterruptedException, ExecutionException {
        String rpc = "deleteA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty("response-code", "200");
        DeleteA1PolicyInputBuilder inputBuilder = new DeleteA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(DeleteA1PolicyOutputBuilder.class),
                any(Properties.class))).thenReturn(respProps);
        ListenableFuture<RpcResult<DeleteA1PolicyOutput>> result =
                a1AdapterProviderMock.deleteA1Policy(inputBuilder.build());
        assertEquals("200", String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    public void test_getA1Policy() throws SvcLogicException, InterruptedException, ExecutionException {
        String rpc = "getA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty("response-code", "200");
        GetA1PolicyInputBuilder inputBuilder = new GetA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(GetA1PolicyOutputBuilder.class),
                any(Properties.class))).thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyOutput>> result = a1AdapterProviderMock.getA1Policy(inputBuilder.build());
        assertEquals("200", String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    public void test_getA1PolicyType() throws SvcLogicException, InterruptedException, ExecutionException {
        String rpc = "getA1PolicyType";
        Properties respProps = new Properties();
        respProps.setProperty("response-code", "200");
        GetA1PolicyTypeInputBuilder inputBuilder = new GetA1PolicyTypeInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(GetA1PolicyTypeOutputBuilder.class),
                any(Properties.class))).thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyTypeOutput>> result =
                a1AdapterProviderMock.getA1PolicyType(inputBuilder.build());
        assertEquals("200", String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    public void test_getA1PolicyStatus() throws SvcLogicException, InterruptedException, ExecutionException {
        String rpc = "getA1PolicyStatus";
        Properties respProps = new Properties();
        respProps.setProperty("response-code", "200");
        GetA1PolicyStatusInputBuilder inputBuilder = new GetA1PolicyStatusInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(GetA1PolicyStatusOutputBuilder.class),
                any(Properties.class))).thenReturn(respProps);
        ListenableFuture<RpcResult<GetA1PolicyStatusOutput>> result =
                a1AdapterProviderMock.getA1PolicyStatus(inputBuilder.build());
        assertEquals("200", String.valueOf(result.get().getResult().getHttpStatus()));
    }

    @Test
    public void test_putA1Policy() throws SvcLogicException, InterruptedException, ExecutionException {
        String rpc = "putA1Policy";
        Properties respProps = new Properties();
        respProps.setProperty("response-code", "200");
        PutA1PolicyInputBuilder inputBuilder = new PutA1PolicyInputBuilder();
        when(a1AdapterClient.hasGraph(module, rpc, null, mode)).thenReturn(true);
        when(a1AdapterClient.execute(eq(module), eq(rpc), eq(null), eq(mode), any(PutA1PolicyOutputBuilder.class),
                any(Properties.class))).thenReturn(respProps);
        ListenableFuture<RpcResult<PutA1PolicyOutput>> result = a1AdapterProviderMock.putA1Policy(inputBuilder.build());
        assertEquals("200", String.valueOf(result.get().getResult().getHttpStatus()));
    }
}
