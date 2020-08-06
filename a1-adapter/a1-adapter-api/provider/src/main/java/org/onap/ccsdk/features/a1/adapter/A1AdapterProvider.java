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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.onap.ccsdk.sli.core.sli.provider.MdsalHelper;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.A1ADAPTERAPIService;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a base implementation for your provider. This class overrides the generated interface
 * from the YANG model and implements the request model for the A1 interface. This class identifies
 * the Near-RT RIC throught the IP passed over the payload and calls the corresponding Near-RT RIC
 * over Rest API
 *
 * <pre>
 *
 */
@SuppressWarnings("squid:S1874") // "@Deprecated" code should not be used
public class A1AdapterProvider implements AutoCloseable, A1ADAPTERAPIService {

  private static final Logger log = LoggerFactory.getLogger(A1AdapterProvider.class);

  private static final String APPLICATION_NAME = "a1Adapter-api";

  private final ExecutorService executor;
  protected DataBroker dataBroker;
  protected NotificationPublishService notificationService;
  protected RpcProviderRegistry rpcRegistry;
  protected BindingAwareBroker.RpcRegistration<A1ADAPTERAPIService> rpcRegistration;
  private final A1AdapterClient a1AdapterClient;

  public A1AdapterProvider(final DataBroker dataBroker,
      final NotificationPublishService notificationPublishService,
      final RpcProviderRegistry rpcProviderRegistry, final A1AdapterClient a1AdapterClient) {

    log.info("Creating provider for {}", APPLICATION_NAME);
    executor = Executors.newFixedThreadPool(1);
    this.dataBroker = dataBroker;
    this.notificationService = notificationPublishService;
    this.rpcRegistry = rpcProviderRegistry;
    this.a1AdapterClient = a1AdapterClient;
    initialize();
  }

  public void initialize() {
    log.info("Initializing provider for {}", APPLICATION_NAME);
    rpcRegistration = rpcRegistry.addRpcImplementation(A1ADAPTERAPIService.class, this);
    log.info("Initialization complete for {}", APPLICATION_NAME);
  }

  protected void initializeChild() {
    // Override if you have custom initialization intelligence
  }

  @Override
  public void close() throws Exception {
    log.info("Closing provider for {}", APPLICATION_NAME);
    executor.shutdown();
    rpcRegistration.close();
    log.info("Successfully closed provider for {}", APPLICATION_NAME);
  }

  @Override
  public ListenableFuture<RpcResult<DeleteA1PolicyOutput>> deleteA1Policy(
      DeleteA1PolicyInput input) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ListenableFuture<RpcResult<GetA1PolicyOutput>> getA1Policy(GetA1PolicyInput input) {
    log.info("Start of getA1Policy");
    return null;
  }

  @Override
  public ListenableFuture<RpcResult<GetA1PolicyStatusOutput>> getA1PolicyStatus(
      GetA1PolicyStatusInput input) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ListenableFuture<RpcResult<GetA1PolicyTypeOutput>> getA1PolicyType(
      GetA1PolicyTypeInput input) {
    log.info("Start of getA1PolicyType");
    final String svcOperation = "getA1PolicyType";
    Properties parms = new Properties();
    GetA1PolicyTypeOutputBuilder policyTypeResponse = new GetA1PolicyTypeOutputBuilder();
    // add input to parms
    log.info("Adding INPUT data for " + svcOperation + " input: " + input);
    GetA1PolicyTypeInputBuilder inputBuilder = new GetA1PolicyTypeInputBuilder(input);
    MdsalHelper.toProperties(parms, inputBuilder.build());
    log.info("Printing SLI parameters to be passed");
    // iterate properties file to get key-value pairs
    for (String key : parms.stringPropertyNames()) {
      String value = parms.getProperty(key);
      log.info("The SLI parameter in " + key + " is: " + value);
    }
    // Call SLI sync method
    try {
      if (a1AdapterClient.hasGraph("A1-ADAPTER-API", svcOperation, null, "sync")) {
        log.info("A1AdapterClient has a Directed Graph for '" + svcOperation + "'");
        try {
          a1AdapterClient.execute("A1-ADAPTER-API", svcOperation, null, "sync", policyTypeResponse,
              parms);
          policyTypeResponse.setHttpStatus(200);
        } catch (Exception e) {
          log.error("Caught exception executing service logic for " + svcOperation, e);
          policyTypeResponse.setHttpStatus(500);
        }
      } else {
        log.error("No service logic active for A1Adapter: '" + svcOperation + "'");
        policyTypeResponse.setHttpStatus(503);
      }
    } catch (Exception e) {
      log.error("Caught exception looking for service logic", e);
      policyTypeResponse.setHttpStatus(500);
    }
    RpcResult<GetA1PolicyTypeOutput> rpcResult = RpcResultBuilder
        .<GetA1PolicyTypeOutput>status(true).withResult(policyTypeResponse.build()).build();
    log.info("End of getA1PolicyType");
    return Futures.immediateFuture(rpcResult);
  }

  @Override
  public ListenableFuture<RpcResult<PutA1PolicyOutput>> putA1Policy(PutA1PolicyInput input) {
    // TODO Auto-generated method stub
    return null;
  }

}
