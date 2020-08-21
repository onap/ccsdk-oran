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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.Collection;

import org.immutables.gson.Gson;

@Gson.TypeAdapters
@ApiModel(value = "policy_schema_list_v2", description = "Policy type json schemas")
public class PolicySchemaList {

    @ApiModelProperty(
        value = "Policy type json schemas. The schema is a json object following http://json-schema.org/draft-07/schema")
    @SerializedName("policy_schemas")
    @JsonProperty("policy_schemas")
    public final Collection<Object> schemas;

    public PolicySchemaList(Collection<String> schemasAsStrings) {
        this.schemas = new ArrayList<>();
        for (String str : schemasAsStrings) {
            JsonObject jsonObj = JsonParser.parseString(str).getAsJsonObject();
            this.schemas.add(jsonObj);
        }
    }

}
