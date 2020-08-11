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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@ApiModel(value = "service_registration_info_v2", description = "Information for one service")
public class ServiceRegistrationInfo {

    @ApiModelProperty(value = "identity of the service", required = true, allowEmptyValue = false)
    @SerializedName(value = "service_id")
    @JsonProperty("service_id")
    public String serviceId = "";

    @ApiModelProperty(
        value = "keep alive interval for the service. This is a heartbeat supervision of the service, "
            + "which in regular intevals must invoke a 'keepAlive' REST call. "
            + "When a service does not invoke this call within the given time, it is considered unavailble. "
            + "An unavailable service will be automatically deregistered and its policies will be deleted. "
            + "Value 0 means no timeout supervision.")
    @SerializedName("keep_alive_interval_seconds")
    @JsonProperty("keep_alive_interval_seconds")
    public long keepAliveIntervalSeconds = 0;

    @ApiModelProperty(value = "callback for notifying of RIC synchronization", required = false, allowEmptyValue = true)
    @SerializedName("callback_url")
    @JsonProperty("callback_url")
    public String callbackUrl = "";

    public ServiceRegistrationInfo() {
    }

    public ServiceRegistrationInfo(String id, long keepAliveIntervalSeconds, String callbackUrl) {
        this.serviceId = id;
        this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
        this.callbackUrl = callbackUrl;
    }

}
