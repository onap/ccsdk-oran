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
@ApiModel(value = "policy_info_v2", description = "Information for one A1-P Policy")
public class PolicyInfo {

    @ApiModelProperty(value = "identity of the policy", required = true)
    @JsonProperty(value = "policy_id", required = true)
    @SerializedName("policy_id")
    public String policyId;

    @ApiModelProperty(value = "identity of the policy type", required = true)
    @JsonProperty(value = "policy_type_id", required = true)
    @SerializedName("policy_type_id")
    public String policyTypeId;

    @ApiModelProperty(value = "identity of the target Near-RT RIC", required = true)
    @JsonProperty(value = "ric_id", required = true)
    @SerializedName("ric_id")
    public String ricId;

    @ApiModelProperty(value = "the configuration of the policy", required = true)
    @JsonProperty(value = "policy_data", required = true)
    @SerializedName("policy_data")
    public Object policyData;

    @ApiModelProperty(value = "the name of the service owning the policy", required = true)
    @JsonProperty(value = "service_id", required = true)
    @SerializedName("service_id")
    public String serviceId;

    @ApiModelProperty(value = "the name of the service owning the policy", required = false)
    @JsonProperty(value = "transient", required = false)
    @SerializedName("transient")
    public boolean isTransient;

    @ApiModelProperty(value = "Callback URI for policy status updates", required = false)
    @JsonProperty(value = "status_notification_uri", required = false)
    @SerializedName("status_notification_uri")
    public String statusNotificationUri = "";

    PolicyInfo() {}

    public boolean validate() {
        return policyId != null && policyTypeId != null && ricId != null && policyData != null && serviceId != null;
    }

}
