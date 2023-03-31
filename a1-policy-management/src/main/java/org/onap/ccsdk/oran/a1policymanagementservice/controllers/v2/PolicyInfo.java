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
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "policy_info_v2", description = "Information for one A1-P Policy")
public class PolicyInfo {

    @Schema(name = "policy_id", description = "identity of the policy", requiredMode = RequiredMode.REQUIRED)
    @JsonProperty(value = "policy_id", required = true)
    @SerializedName("policy_id")
    public String policyId;

    @Schema(name = "policytype_id", description = "identity of the policy type", requiredMode = RequiredMode.REQUIRED)
    @JsonProperty(value = "policytype_id", required = true)
    @SerializedName("policytype_id")
    public String policyTypeId;

    @Schema(name = "ric_id", description = "identity of the target Near-RT RIC", requiredMode = RequiredMode.REQUIRED)
    @JsonProperty(value = "ric_id", required = true)
    @SerializedName("ric_id")
    public String ricId;

    @Schema(name = "policy_data", description = "the configuration of the policy", requiredMode = RequiredMode.REQUIRED)
    @JsonProperty(value = "policy_data", required = true)
    @SerializedName("policy_data")
    public Object policyData;

    private static final String SERVICE_ID_DESCRIPTION = "the identity of the service owning the policy."
            + " This can be used to group the policies (it is possible to get all policies associated to a service)."
            + " Note that the service does not need to be registered.";

    @Schema(name = "service_id", description = SERVICE_ID_DESCRIPTION, requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "")
    @JsonProperty(value = "service_id", required = false)
    @SerializedName("service_id")
    public String serviceId = "";

    @Schema(name = "transient",
            description = "if true, the policy is deleted at RIC restart. If false, its value is maintained by this service until explicitly deleted. Default false.",
            requiredMode = RequiredMode.NOT_REQUIRED, defaultValue = "false", example = "false")
    @JsonProperty(value = "transient", required = false, defaultValue = "false")
    @SerializedName("transient")
    public boolean isTransient = false;

    @Schema(name = "status_notification_uri", description = "Callback URI for policy status updates", requiredMode = RequiredMode.NOT_REQUIRED,
            defaultValue = "")
    @JsonProperty(value = "status_notification_uri", required = false)
    @SerializedName("status_notification_uri")
    public String statusNotificationUri = "";

    PolicyInfo() {}

    public boolean validate() {
        return policyId != null && policyTypeId != null && ricId != null && policyData != null && serviceId != null;
    }

}
