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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@Schema(name = "service_callback_info_v2",
        description = "Information transferred as in Service callbacks (callback_url)")
public class ServiceCallbackInfo {

    private static final String EVENT_TYPE_DESCRIPTION = "values:\n" //
            + "AVAILABLE: the  Near-RT RIC has become available for A1 Policy management";

    @Gson.TypeAdapters
    @Schema(name = "event_type_v2", description = EVENT_TYPE_DESCRIPTION)
    public enum EventType {
        AVAILABLE
    }

    @Schema(name = "ric_id", description = "identity of a Near-RT RIC", required = true)
    @SerializedName("ric_id")
    @JsonProperty(value = "ric_id", required = true)
    public String ricId;

    @Schema(name = "event_type", description = EVENT_TYPE_DESCRIPTION, required = true)
    @SerializedName("event_type")
    @JsonProperty(value = "event_type", required = true)
    public EventType eventType;

    public ServiceCallbackInfo(String ricId, EventType eventType) {
        this.ricId = ricId;
        this.eventType = eventType;
    }

    public ServiceCallbackInfo() {}
}
