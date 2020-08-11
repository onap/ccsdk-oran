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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Collection;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@ApiModel(value = "ric_info_v2", description = "Information for one NearRT-RIC")
class RicInfo {
    @ApiModelProperty(value = "identity of the ric")
    @SerializedName("ric_id")
    @JsonProperty("ric_id")
    public final String ricId;

    @ApiModelProperty(value = "O1 identities for managed entities")
    @SerializedName("managed_element_ids")
    @JsonProperty("managed_element_ids")
    public final Collection<String> managedElementIds;

    @ApiModelProperty(value = "supported policy types")
    @SerializedName("policy_type_ids")
    @JsonProperty("policy_type_ids")
    public final Collection<String> policyTypeIds;

    @ApiModelProperty(value = "state info", name = "state")
    @SerializedName("state")
    @JsonProperty("state")
    public final String state;

    RicInfo(String ricId, Collection<String> managedElementIds, Collection<String> policyTypes, String state) {
        this.ricId = ricId;
        this.managedElementIds = managedElementIds;
        this.policyTypeIds = policyTypes;
        this.state = state;
    }
}
