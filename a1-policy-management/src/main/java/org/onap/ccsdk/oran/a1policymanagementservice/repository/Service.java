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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

public class Service {

    static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public Instant read(JsonReader reader) throws IOException {
            reader.skipValue();
            return Instant.now(); // Pretend that the last ping was now (after a restart)
        }

        @Override
        public void write(JsonWriter writer, Instant value) throws IOException {
            writer.value(value.toString());
        }
    }

    static class DurationAdapter extends TypeAdapter<Duration> {
        @Override
        public Duration read(JsonReader reader) throws IOException {
            long value = reader.nextLong();
            return Duration.ofNanos(value);
        }

        @Override
        public void write(JsonWriter writer, Duration value) throws IOException {
            writer.value(value.toNanos());
        }
    }

    public static Gson createGson() {
        return new GsonBuilder() //
                .registerTypeAdapter(Instant.class, new Service.InstantAdapter()) //
                .registerTypeAdapter(Duration.class, new Service.DurationAdapter()) //
                .create();
    }

    @Getter
    private final String name;

    @Getter
    private final Duration keepAliveInterval;

    private Instant lastPing;

    @Getter
    @Setter // For test
    private String callbackUrl;

    public Service(String name, Duration keepAliveInterval, String callbackUrl) {
        this.name = name;
        this.keepAliveInterval = keepAliveInterval;
        this.callbackUrl = callbackUrl;
        keepAlive();
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

}
