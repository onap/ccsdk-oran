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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@ApiModel(value = "PolicyInfoV2", description = "Information for one A1-P Policy")
public class PolicyInfo {

    @ApiModelProperty(value = "identity of the policy")
    public String policyId;

    @ApiModelProperty(value = "name of the policy type")
    public String policyTypeId;

    @ApiModelProperty(value = "identity of the target Near-RT RIC")
    public String ricId;

    @ApiModelProperty(value = "the configuration of the policy")
    public Object policyData;

    @ApiModelProperty(value = "the name of the service owning the policy")
    public String serviceId;

    @ApiModelProperty(value = "timestamp, last modification time")
    public String lastModified;

    PolicyInfo() {
    }

    public boolean validate() {
        return policyId != null && policyTypeId != null && ricId != null && policyData != null && serviceId != null
            && lastModified != null;
    }

}
