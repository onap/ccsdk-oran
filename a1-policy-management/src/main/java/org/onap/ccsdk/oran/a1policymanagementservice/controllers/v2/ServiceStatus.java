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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@Schema(name = "service_status_v2")
public class ServiceStatus {

    @Schema(description = "identity of the service")
    @SerializedName("service_id")
    @JsonProperty("service_id")
    public final String serviceId;

    @Schema(description = "policy keep alive timeout")
    @SerializedName("keep_alive_interval_seconds")
    @JsonProperty("keep_alive_interval_seconds")
    public final long keepAliveIntervalSeconds;

    @Schema(description = "time since last invocation by the service")
    @SerializedName("time_since_last_activity_seconds")
    @JsonProperty("time_since_last_activity_seconds")
    public final long timeSinceLastActivitySeconds;

    @Schema(description = "callback for notifying of RIC synchronization")
    @SerializedName("callback_url")
    @JsonProperty("callback_url")
    public String callbackUrl;

    ServiceStatus(String id, long keepAliveIntervalSeconds, long timeSincePingSeconds, String callbackUrl) {
        this.serviceId = id;
        this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
        this.timeSinceLastActivitySeconds = timeSincePingSeconds;
        this.callbackUrl = callbackUrl;
    }

}
