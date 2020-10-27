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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import java.util.List;

import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Common interface for 'A1' Policy access. Implementations of this interface
 * adapts to the different southbound REST APIs supported.
 */
public interface A1Client {

    public enum A1ProtocolType {
        UNKNOWN, //
        STD_V1_1, // STD A1 version 1.1
        STD_V2_0_0, // STD A1 version 2.0.0
        OSC_V1, // OSC 'A1'
        CCSDK_A1_ADAPTER_STD_V1_1, // CCSDK_A1_ADAPTER with STD A1 version 1.1 southbound
        CCSDK_A1_ADAPTER_STD_V2_0_0, // CCSDK_A1_ADAPTER with STD A1 version 2.0.0 southbound
        CCSDK_A1_ADAPTER_OSC_V1 // CCSDK_A1_ADAPTER with OSC 'A1' southbound
    }

    public Mono<A1ProtocolType> getProtocolVersion();

    public Mono<List<String>> getPolicyTypeIdentities();

    public Mono<List<String>> getPolicyIdentities();

    public Mono<String> getPolicyTypeSchema(String policyTypeId);

    public Mono<String> putPolicy(Policy policy);

    public Mono<String> deletePolicy(Policy policy);

    public Flux<String> deleteAllPolicies();

    public Mono<String> getPolicyStatus(Policy policy);

}
