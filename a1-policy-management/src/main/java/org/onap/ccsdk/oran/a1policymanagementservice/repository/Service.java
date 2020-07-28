/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020 Nordix Foundation
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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;

public class Service {
    @Getter
    private final String name;
    private final Duration keepAliveInterval;
    private Instant lastPing;
    private final String callbackUrl;

    public Service(String name, Duration keepAliveInterval, String callbackUrl) {
        this.name = name;
        this.keepAliveInterval = keepAliveInterval;
        this.callbackUrl = callbackUrl;
        keepAlive();
    }

    public synchronized Duration getKeepAliveInterval() {
        return this.keepAliveInterval;
    }

    public synchronized void keepAlive() {
        this.lastPing = Instant.now();
    }

    public synchronized boolean isExpired() {
        return this.keepAliveInterval.getSeconds() > 0 && timeSinceLastPing().compareTo(this.keepAliveInterval) > 0;
    }

    public synchronized Duration timeSinceLastPing() {
        return Duration.between(this.lastPing, Instant.now());
    }

    public synchronized String getCallbackUrl() {
        return this.callbackUrl;
    }

}
