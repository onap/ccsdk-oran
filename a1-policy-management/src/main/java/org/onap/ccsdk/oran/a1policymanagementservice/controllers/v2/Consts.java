/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

public class Consts {
    public static final String RIC_ID_PARAM = "ric_id";
    public static final String POLICY_TYPE_ID_PARAM = "policytype_id";
    public static final String POLICY_ID_PARAM = "policy_id";
    public static final String SERVICE_ID_PARAM = "service_id";
    public static final String TRANSIENT_PARAM = "transient";
    public static final String MANAGED_ELEMENT_ID_PARAM = "managed_element_id";
    public static final String TYPE_NAME_PARAM = "type_name";
    public static final String COMPATIBLE_WITH_VERSION_PARAM = "compatible_with_version";

    public static final String V2_API_ROOT = "/a1-policy/v2";

    public static final String V3_API_ROOT = "/a1policymanagement/v1";

    public static final String V2_API_SERVICE_CALLBACKS_NAME = "Service callbacks";
    public static final String V2_API_SERVICE_CALLBACKS_DESCRIPTION = "";

    private Consts() {}
}
