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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.sli.core.sli.SvcLogicException;
import org.onap.ccsdk.sli.core.sli.provider.SvcLogicService;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutputBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class A1AdapterClientTest {

    protected static final Logger LOG = LoggerFactory.getLogger(A1AdapterClientTest.class);

    @Mock
    private SvcLogicService svcLogicService = null;
    private A1AdapterClient a1AdapterClient;
    private static String module = "A1-ADAPTER-API";
    private static String mode = "sync";

    @BeforeEach
    void setUp() {
        a1AdapterClient = new A1AdapterClient(svcLogicService);
    }

    @Test
    void getPolicyType() throws Exception {
        String rpc = "deleteA1Policy";
        Properties params = new Properties();
        Properties respProps = new Properties();
        GetA1PolicyTypeOutputBuilder serviceData = new GetA1PolicyTypeOutputBuilder();
        when(svcLogicService.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Properties.class)))
                .thenReturn(respProps);
        Properties response = a1AdapterClient.execute(module, rpc, null, mode, serviceData, params);
        assertNotNull(response);
    }

    @Test
    void getPolicyStatus() throws Exception {
        String rpc = "getA1PolicyStatus";
        Properties params = new Properties();
        Properties respProps = new Properties();
        GetA1PolicyStatusOutputBuilder serviceData = new GetA1PolicyStatusOutputBuilder();
        when(svcLogicService.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Properties.class)))
                .thenReturn(respProps);
        Properties response = a1AdapterClient.execute(module, rpc, null, mode, serviceData, params);
        assertNotNull(response);
    }

    @Test
    void getPolicy() throws Exception {
        String rpc = "getA1Policy";
        Properties params = new Properties();
        Properties respProps = new Properties();
        GetA1PolicyOutputBuilder serviceData = new GetA1PolicyOutputBuilder();
        when(svcLogicService.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Properties.class)))
                .thenReturn(respProps);
        Properties response = a1AdapterClient.execute(module, rpc, null, mode, serviceData, params);
        assertNotNull(response);
    }

    @Test
    void deletePolicy() throws Exception {
        String rpc = "deleteA1Policy";
        Properties params = new Properties();
        Properties respProps = new Properties();
        DeleteA1PolicyOutputBuilder serviceData = new DeleteA1PolicyOutputBuilder();
        when(svcLogicService.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Properties.class)))
                .thenReturn(respProps);
        Properties response = a1AdapterClient.execute(module, rpc, null, mode, serviceData, params);
        assertNotNull(response);
    }

    @Test
    void putPolicy() throws Exception {
        String rpc = "putA1Policy";
        Properties params = new Properties();
        Properties respProps = new Properties();
        PutA1PolicyOutputBuilder serviceData = new PutA1PolicyOutputBuilder();
        when(svcLogicService.execute(eq(module), eq(rpc), eq(null), eq(mode), any(Properties.class)))
                .thenReturn(respProps);
        Properties response = a1AdapterClient.execute(module, rpc, null, mode, serviceData, params);
        assertNotNull(response);
    }
}
