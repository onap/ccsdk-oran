/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Schema(name = "policy_authorization", description = "Authorization request for A1 policy requests")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class PolicyAuthorizationRequest {

    @Schema(name = "input", description = "input")
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    public static class Input {

        @Schema(name = "acces_type", description = "Access type")
        public enum AccessType {
            READ, WRITE, DELETE
        }

        @Schema(name = "access_type", description = "Access type", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty(value = "access_type", required = true)
        @SerializedName("access_type")
        @Getter
        private AccessType accessType;

        @Schema(name = "policy_type_id", description = "Policy type identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        @SerializedName("policy_type_id")
        @JsonProperty(value = "policy_type_id", required = true)
        private String policyTypeId;

        @Schema(name = "auth_token", description = "Authorization token", requiredMode = Schema.RequiredMode.REQUIRED)
        @SerializedName("auth_token")
        @JsonProperty(value = "auth_token", required = true)
        private String authToken;

    }

    @Schema(name = "input", description = "Input", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty(value = "input", required = true)
    @SerializedName("input")
    private Input input;

}
