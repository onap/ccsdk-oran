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

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.reflect.Method;
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
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.DeleteA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyStatusOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.GetA1PolicyTypeOutputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyInput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyInputBuilder;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutput;
import org.opendaylight.yang.gen.v1.org.onap.a1.adapter.rev200122.PutA1PolicyOutputBuilder;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class overrides the generated interface from the YANG model and implements the request model for the A1
 * interface. This class identifies the Near-RT RIC throught the IP passed over the payload and calls the corresponding
 * Near-RT RIC over Rest API.
 */
@SuppressWarnings("squid:S1874") // "@Deprecated" code should not be used
public class A1AdapterProvider implements AutoCloseable, A1ADAPTERAPIService {

    private static final String START_OPERATION_MESSAGE = "Start of {}";
    private static final String END_OPERATION_MESSAGE = "End of {}";

    private static final String A1_ADAPTER_API = "A1-ADAPTER-API";
    private static final String RESPONSE_BODY = "responseBody";
    private static final String RESPONSE_CODE = "response-code";
    private static final String SYNC = "sync";

    private static final Logger log = LoggerFactory.getLogger(A1AdapterProvider.class);

    private static final String APPLICATION_NAME = "a1Adapter-api";

    private final ExecutorService executor;
    protected DataBroker dataBroker;
    protected NotificationPublishService notificationService;
    protected RpcProviderRegistry rpcRegistry;
    protected BindingAwareBroker.RpcRegistration<A1ADAPTERAPIService> rpcRegistration;
    private final A1AdapterClient a1AdapterClient;

    public A1AdapterProvider(final DataBroker dataBroker, final NotificationPublishService notificationPublishService,
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
    public ListenableFuture<RpcResult<DeleteA1PolicyOutput>> deleteA1Policy(DeleteA1PolicyInput deletePolicyInput) {
        final String svcOperation = "deleteA1Policy";
        log.info(START_OPERATION_MESSAGE, svcOperation);
        DeleteA1PolicyOutputBuilder deletePolicyResponse = new DeleteA1PolicyOutputBuilder();
        setUpAndExecuteOperation(svcOperation, new DeleteA1PolicyInputBuilder(deletePolicyInput), deletePolicyResponse);

        RpcResult<DeleteA1PolicyOutput> deletePolicyResult =
            RpcResultBuilder.<DeleteA1PolicyOutput>status(true).withResult(deletePolicyResponse.build()).build();
        log.info(END_OPERATION_MESSAGE, svcOperation);
        return Futures.immediateFuture(deletePolicyResult);
    }

    @Override
    public ListenableFuture<RpcResult<GetA1PolicyOutput>> getA1Policy(GetA1PolicyInput getPolicyInput) {
        final String svcOperation = "getA1Policy";
        log.info(START_OPERATION_MESSAGE, svcOperation);
        GetA1PolicyOutputBuilder getPolicyResponse = new GetA1PolicyOutputBuilder();
        setUpAndExecuteOperation(svcOperation, new GetA1PolicyInputBuilder(getPolicyInput), getPolicyResponse);

        RpcResult<GetA1PolicyOutput> getPolicyResult =
            RpcResultBuilder.<GetA1PolicyOutput>status(true).withResult(getPolicyResponse.build()).build();
        log.info(END_OPERATION_MESSAGE, svcOperation);
        return Futures.immediateFuture(getPolicyResult);
    }

    @Override
    public ListenableFuture<RpcResult<GetA1PolicyStatusOutput>> getA1PolicyStatus(
        GetA1PolicyStatusInput getPolicyStatusInput) {
        final String svcOperation = "getA1PolicyStatus";
        log.info(START_OPERATION_MESSAGE, svcOperation);
        GetA1PolicyStatusOutputBuilder getPolicyStatusResponse = new GetA1PolicyStatusOutputBuilder();
        setUpAndExecuteOperation(svcOperation, new GetA1PolicyStatusInputBuilder(getPolicyStatusInput),
            getPolicyStatusResponse);

        RpcResult<GetA1PolicyStatusOutput> getPolicyStatusResult =
            RpcResultBuilder.<GetA1PolicyStatusOutput>status(true).withResult(getPolicyStatusResponse.build()).build();
        log.info(END_OPERATION_MESSAGE, svcOperation);
        return Futures.immediateFuture(getPolicyStatusResult);
    }

    @Override
    public ListenableFuture<RpcResult<GetA1PolicyTypeOutput>> getA1PolicyType(GetA1PolicyTypeInput getPolicyTypeInput) {
        final String svcOperation = "getA1PolicyType";
        log.info(START_OPERATION_MESSAGE, svcOperation);
        GetA1PolicyTypeOutputBuilder getPolicyTypeResponse = new GetA1PolicyTypeOutputBuilder();
        setUpAndExecuteOperation(svcOperation, new GetA1PolicyTypeInputBuilder(getPolicyTypeInput),
            getPolicyTypeResponse);

        RpcResult<GetA1PolicyTypeOutput> getPolicyTypeResult =
            RpcResultBuilder.<GetA1PolicyTypeOutput>status(true).withResult(getPolicyTypeResponse.build()).build();
        log.info(END_OPERATION_MESSAGE, svcOperation);
        return Futures.immediateFuture(getPolicyTypeResult);
    }

    @Override
    public ListenableFuture<RpcResult<PutA1PolicyOutput>> putA1Policy(PutA1PolicyInput putPolicyInput) {
        final String svcOperation = "putA1Policy";
        log.info(START_OPERATION_MESSAGE, svcOperation);
        PutA1PolicyOutputBuilder putPolicyResponse = new PutA1PolicyOutputBuilder();
        setUpAndExecuteOperation(svcOperation, new PutA1PolicyInputBuilder(putPolicyInput), putPolicyResponse);

        RpcResult<PutA1PolicyOutput> putPolicyResult =
            RpcResultBuilder.<PutA1PolicyOutput>status(true).withResult(putPolicyResponse.build()).build();
        log.info(END_OPERATION_MESSAGE, svcOperation);
        return Futures.immediateFuture(putPolicyResult);
    }

    private <T> boolean hasGraph(final String svcOperation, Builder<T> response) {
        try {
            return a1AdapterClient.hasGraph(A1_ADAPTER_API, svcOperation, null, SYNC);
        } catch (Exception e) {
            log.error("Caught exception looking for service logic, {}", e.getMessage());
            setHttpResponse(response, SC_INTERNAL_SERVER_ERROR);
        }
        return false;
    }

    private <U, T> void setUpAndExecuteOperation(final String svcOperation, Builder<U> inputBuilder,
        Builder<T> responseBuilder) {
        log.info("Adding INPUT data for {} input: {}", svcOperation, inputBuilder);
        // add input to parms
        Properties parms = new Properties();
        MdsalHelper.toProperties(parms, inputBuilder.build());
        logSliParameters(parms);
        // Call SLI sync method
        if (hasGraph(svcOperation, responseBuilder)) {
            log.info("A1AdapterClient has a Directed Graph for '{}'", svcOperation);
            executeOperation(svcOperation, parms, responseBuilder);
        } else {
            log.error("No service logic active for A1Adapter: '{}'", svcOperation);
            setHttpResponse(responseBuilder, Integer.valueOf(SC_SERVICE_UNAVAILABLE));
        }
    }

    private <T> void executeOperation(final String svcOperation, Properties parms, Builder<T> response) {
        try {
            Properties responseParms =
                a1AdapterClient.execute(A1_ADAPTER_API, svcOperation, null, SYNC, response, parms);
            logResponse(responseParms);
            setBody(response, responseParms.getProperty(RESPONSE_BODY));
            setHttpResponse(response, Integer.valueOf(responseParms.getProperty(RESPONSE_CODE)));
        } catch (Exception e) {
            log.error("Caught exception executing service logic for {}, {}", svcOperation, e.getMessage());
            setHttpResponse(response, Integer.valueOf(SC_INTERNAL_SERVER_ERROR));
        }
    }

    private <T> void setBody(Builder<T> responseBuilder, String body) {
        try {
            Method method = responseBuilder.getClass().getMethod("setBody", String.class);
            method.invoke(responseBuilder, body);
        } catch (Exception reflectionException) {
            throw new MissingResponseMethodRuntimeException(reflectionException);
        }
    }

    private <T> void setHttpResponse(Builder<T> responseBuilder, Integer response) {
        try {
            Method method = responseBuilder.getClass().getMethod("setHttpStatus", Integer.class);
            method.invoke(responseBuilder, response);
        } catch (Exception reflectionException) {
            throw new MissingResponseMethodRuntimeException(reflectionException);
        }
    }

    private void logSliParameters(Properties parms) {
        log.info("Printing SLI parameters to be passed");
        // iterate properties file to get key-value pairs
        for (String key : parms.stringPropertyNames()) {
            String value = parms.getProperty(key);
            log.info("The SLI parameter in {} is: {}", key, value);
        }
    }

    private void logResponse(Properties responseParms) {
        log.info("responseBody::{}", responseParms.getProperty(RESPONSE_BODY));
        log.info("responseCode::{}", responseParms.getProperty(RESPONSE_CODE));
        log.info("responseMessage::{}", responseParms.getProperty("response-message"));
    }

    public class MissingResponseMethodRuntimeException extends RuntimeException {
        private static final long serialVersionUID = -6803869291161765099L;

        MissingResponseMethodRuntimeException(Exception e) {
            super(e);
        }
    }
}
