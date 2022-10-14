/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2022 Nordix Foundation. All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class NullStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public NullStore(String location) {
        logger.warn("No storage defined for: {}", location);
    }

    @Override
    public Flux<String> listObjects(String prefix) {
        return Flux.empty();
    }

    @Override
    public Mono<byte[]> readObject(String name) {
        return Mono.just(new byte[0]);
    }

    @Override
    public Mono<byte[]> writeObject(String name, byte[] fileData) {
        return Mono.just(new byte[0]);
    }

    @Override
    public Mono<Boolean> deleteObject(String name) {
        return Mono.just(false);
    }

    @Override
    public Mono<String> createDataStore() {
        return Mono.just("");
    }

    @Override
    public Mono<String> deleteAllObjects() {
        return Mono.just("");
    }

}
