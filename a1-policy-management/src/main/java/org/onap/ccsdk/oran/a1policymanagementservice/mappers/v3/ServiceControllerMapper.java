/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.mappers.v3;

import org.mapstruct.Mapper;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceRegistrationInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ServiceStatusList;

@Mapper(componentModel = "spring")
public interface ServiceControllerMapper {

    ServiceRegistrationInfo toServiceRegistrationInfoV2(
            org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ServiceRegistrationInfo serviceRegistrationInfo);

    ServiceStatusList toServiceStatusListV3(
            org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceStatusList serviceStatusList);
}
