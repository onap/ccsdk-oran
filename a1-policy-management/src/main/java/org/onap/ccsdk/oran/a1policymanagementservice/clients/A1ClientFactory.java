/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020 Nordix Foundation. All rights reserved.
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

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ControllerConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * Factory for A1 clients that supports four different protocol versions of the
 * A1 api.
 */
public class A1ClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(A1ClientFactory.class);

    private final ApplicationConfig appConfig;

    private final AsyncRestClientFactory restClientFactory;

    @Autowired
    public A1ClientFactory(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
        this.restClientFactory = new AsyncRestClientFactory(appConfig.getWebClientConfig());
    }

    /**
     * Creates an A1 client with the correct A1 protocol for the provided Ric.
     *
     * <p>
     * It detects the protocol version by trial and error, since there is no
     * getVersion method specified in the A1 api yet.
     *
     * <p>
     * As a side effect it also sets the protocol version in the provided Ric. This
     * means that after the first successful creation it won't have to try which
     * protocol to use, but can create the client directly.
     *
     * @param ric The Near-RT RIC to get a client for.
     * @return a client with the correct protocol, or a ServiceException if none of
     *         the protocols are supported by the Near-RT RIC.
     */
    public Mono<A1Client> createA1Client(Ric ric) {
        return getProtocolVersion(ric) //
                .flatMap(version -> createA1ClientMono(ric, version));
    }

    A1Client createClient(Ric ric, A1ProtocolType version) throws ServiceException {
        if (version == A1ProtocolType.STD_V1_1) {
            assertNoControllerConfig(ric, version);
            return new StdA1ClientVersion1(ric.getConfig(), this.restClientFactory);
        } else if (version == A1ProtocolType.STD_V2_0_0) {
            assertNoControllerConfig(ric, version);
            return new StdA1ClientVersion2(ric.getConfig(), this.restClientFactory);
        } else if (version == A1ProtocolType.OSC_V1) {
            assertNoControllerConfig(ric, version);
            return new OscA1Client(ric.getConfig(), this.restClientFactory);
        } else if (version == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1
                || version == A1ProtocolType.CCSDK_A1_ADAPTER_OSC_V1
                || version == A1ProtocolType.CCSDK_A1_ADAPTER_STD_V2_0_0) {
            return new CcsdkA1AdapterClient(version, ric.getConfig(), getControllerConfig(ric), this.restClientFactory);
        } else {
            logger.error("Unhandled protocol: {}", version);
            throw new ServiceException("Unhandled protocol");
        }
    }

    private ControllerConfig getControllerConfig(Ric ric) throws ServiceException {
        String controllerName = ric.getConfig().controllerName();
        if (controllerName.isEmpty()) {
            ric.setProtocolVersion(A1ProtocolType.UNKNOWN);
            throw new ServiceException("No controller configured for Near-RT RIC: " + ric.id());
        }
        try {
            return this.appConfig.getControllerConfig(controllerName);
        } catch (ServiceException e) {
            ric.setProtocolVersion(A1ProtocolType.UNKNOWN);
            throw e;
        }
    }

    private void assertNoControllerConfig(Ric ric, A1ProtocolType version) throws ServiceException {
        if (!ric.getConfig().controllerName().isEmpty()) {
            ric.setProtocolVersion(A1ProtocolType.UNKNOWN);
            throw new ServiceException(
                    "Controller config should be empty, ric: " + ric.id() + " when using protocol version: " + version);
        }
    }

    private Mono<A1Client> createA1ClientMono(Ric ric, A1ProtocolType version) {
        try {
            return Mono.just(createClient(ric, version));
        } catch (ServiceException e) {
            return Mono.error(e);
        }
    }

    private Mono<A1Client.A1ProtocolType> getProtocolVersion(Ric ric) {
        if (ric.getProtocolVersion() == A1ProtocolType.UNKNOWN) {
            return fetchVersion(ric, A1ProtocolType.STD_V2_0_0) //
                    .onErrorResume(notUsed -> fetchVersion(ric, A1ProtocolType.STD_V1_1)) //
                    .onErrorResume(notUsed -> fetchVersion(ric, A1ProtocolType.OSC_V1)) //
                    .onErrorResume(notUsed -> fetchVersion(ric, A1ProtocolType.CCSDK_A1_ADAPTER_STD_V1_1)) //
                    .doOnNext(ric::setProtocolVersion)
                    .doOnNext(version -> logger.debug("Established protocol version:{} for Near-RT RIC: {}", version,
                            ric.id())) //
                    .doOnError(notUsed -> logger.warn("Could not get protocol version from Near-RT RIC: {}", ric.id())) //
                    .onErrorResume(
                            notUsed -> Mono.error(new ServiceException("Protocol negotiation failed for " + ric.id())));
        } else {
            return Mono.just(ric.getProtocolVersion());
        }
    }

    private Mono<A1ProtocolType> fetchVersion(Ric ric, A1ProtocolType protocolType) {
        return createA1ClientMono(ric, protocolType) //
                .flatMap(A1Client::getProtocolVersion);
    }
}
