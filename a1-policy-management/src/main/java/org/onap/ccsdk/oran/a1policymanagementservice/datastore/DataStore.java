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

import com.google.common.base.Strings;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DataStore {

    public Flux<String> listObjects(String prefix);

    public Mono<byte[]> readObject(String name);

    public Mono<byte[]> writeObject(String name, byte[] fileData);

    public Mono<Boolean> deleteObject(String name);

    public Mono<String> createDataStore();

    public Mono<String> deleteAllObjects();

    public static DataStore create(ApplicationConfig appConfig, String location) {
        if (appConfig.isDatabaseEnabled()) {
            return new DatabaseStore(location);
        } else if (appConfig.isS3Enabled()) {
            return new S3ObjectStore(appConfig, location);
        } else if (!Strings.isNullOrEmpty(appConfig.getVardataDirectory())) {
            return new FileStore(appConfig, location);
        } else {
            return new NullStore(location);
        }

    }

}
