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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collection;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@Schema(name = "ric_info_v2", description = "Information for a Near-RT RIC")
public class RicInfo {

    @Gson.TypeAdapters
    @Schema(name = "ric_state_v2", description = "Represents the states for a Near-RT RIC")
    public enum RicState {
        UNAVAILABLE, AVAILABLE, SYNCHRONIZING, CONSISTENCY_CHECK
    }

    private static final String STATE_DESCRIPTION = "State for the Near-RT RIC, values: \n"
            + "UNAVAILABLE: The Near-RT RIC is not avialable, information may be inconsistent \n"
            + "AVAILABLE: The normal state. Policies can be configured. +\n"
            + "SYNCHRONIZING: The Policy Management Service is synchronizing the view of the Near-RT RIC. Policies cannot be configured. \n"
            + "CONSISTENCY_CHECK: A consistency check between the Policy Management Service and the Near-RT RIC. Policies cannot be configured.";

    @Schema(description = "identity of the Near-RT RIC")
    @SerializedName("ric_id")
    @JsonProperty("ric_id")
    public final String ricId;

    @Schema(description = "O1 identities for managed entities")
    @SerializedName("managed_element_ids")
    @JsonProperty("managed_element_ids")
    public final Collection<String> managedElementIds;

    @Schema(description = "supported policy types")
    @SerializedName("policytype_ids")
    @JsonProperty("policytype_ids")
    public final Collection<String> policyTypeIds;

    @Schema(description = STATE_DESCRIPTION, name = "state")
    @SerializedName("state")
    @JsonProperty("state")
    public final RicState state;

    RicInfo(String ricId, Collection<String> managedElementIds, Collection<String> policyTypes, RicState state) {
        this.ricId = ricId;
        this.managedElementIds = managedElementIds;
        this.policyTypeIds = policyTypes;
        this.state = state;
    }
}
