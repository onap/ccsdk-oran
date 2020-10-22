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

import java.time.Instant;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@ApiModel(value = "policy_status_info_v2", description = "Status for one A1-P Policy")
public class PolicyStatusInfo {

    @ApiModelProperty(value = "timestamp, last modification time")
    @SerializedName("last_modified")
    @JsonProperty("last_modified")
    public String lastModified;

    @ApiModelProperty(value = "the Policy status")
    @SerializedName("status")
    @JsonProperty("status")
    public Object status;

    public PolicyStatusInfo() {}

    public PolicyStatusInfo(Instant lastModified, Object statusFromNearRTRic) {
        this.lastModified = lastModified.toString();
        this.status = statusFromNearRTRic;
    }

    public boolean validate() {
        return lastModified != null;
    }

}
