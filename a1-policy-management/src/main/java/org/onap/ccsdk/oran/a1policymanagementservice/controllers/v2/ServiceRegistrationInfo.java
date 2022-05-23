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

@Schema(name = "service_registration_info_v2", description = "Information for one service")
public class ServiceRegistrationInfo {

    @Schema(description = "identity of the service", required = true)
    @SerializedName("service_id")
    @JsonProperty("service_id")
    public String serviceId = "";

    @Schema(description = "keep alive interval for the service. This is used to enable optional heartbeat supervision of the service. "
            + "If set (> 0) the registered service should regularly invoke a 'keepalive' REST call. "
            + "When a service fails to invoke this 'keepalive' call within the configured time, the service is considered unavailable. "
            + "An unavailable service will be automatically deregistered and its policies will be deleted. "
            + "Value 0 means timeout supervision is disabled.")
    @SerializedName("keep_alive_interval_seconds")
    @JsonProperty("keep_alive_interval_seconds")
    public long keepAliveIntervalSeconds = 0;

    @Schema(description = "callback for notifying of Near-RT RIC state changes", required = false)
    @SerializedName("callback_url")
    @JsonProperty("callback_url")
    public String callbackUrl = "";

    public ServiceRegistrationInfo() {}

    public ServiceRegistrationInfo(String id, long keepAliveIntervalSeconds, String callbackUrl) {
        this.serviceId = id;
        this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
        this.callbackUrl = callbackUrl;
    }

}
