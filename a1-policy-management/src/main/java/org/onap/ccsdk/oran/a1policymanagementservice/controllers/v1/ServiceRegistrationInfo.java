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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v1;

import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@Schema(name = "service_registration_info_v1")
public class ServiceRegistrationInfo {

    @SerializedName("serviceName")
    public String serviceName = "";

    @Schema(description = "keep alive interval for the service. This is a heartbeat supervision of the service, "
            + "which in regular intevals must invoke a 'keepAlive' REST call. "
            + "When a service does not invoke this call within the given time, it is considered unavailble. "
            + "An unavailable service will be automatically deregistered and its policies will be deleted. "
            + "Value 0 means no timeout supervision.")
    @SerializedName("keepAliveIntervalSeconds")
    public long keepAliveIntervalSeconds = 0;

    @Schema(description = "callback for notifying of RIC synchronization")
    @SerializedName("callbackUrl")
    public String callbackUrl = "";

    public ServiceRegistrationInfo() {}

    public ServiceRegistrationInfo(String name, long keepAliveIntervalSeconds, String callbackUrl) {
        this.serviceName = name;
        this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
        this.callbackUrl = callbackUrl;
    }

}
