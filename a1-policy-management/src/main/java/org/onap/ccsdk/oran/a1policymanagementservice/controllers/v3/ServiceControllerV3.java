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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v3;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v3.ServiceRegistryAndSupervisionApi;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ServiceController;
import org.onap.ccsdk.oran.a1policymanagementservice.mappers.v3.ServiceControllerMapper;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ServiceRegistrationInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ServiceStatusList;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.ErrorHandlingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("serviceControllerV3")
@RequiredArgsConstructor
@Tag( //
        name = ServiceControllerV3.API_NAME, //
        description = ServiceControllerV3.API_DESCRIPTION //
)
@RequestMapping(Consts.V3_API_ROOT)
public class ServiceControllerV3 implements ServiceRegistryAndSupervisionApi {

    public static final String API_NAME = "Service Registry and Supervision";
    public static final String API_DESCRIPTION = "API used to keep the service Alive with in the timeout period";

    private final ServiceController serviceController;

    private final ServiceControllerMapper serviceControllerMapper;

    private final ErrorHandlingService errorHandlingService;

    @Override
    public Mono<ResponseEntity<Object>> deleteService(String serviceId, String accept, ServerWebExchange exchange) throws Exception {
        return serviceController.deleteService(serviceId, exchange);
    }

    @Override
    public Mono<ResponseEntity<ServiceStatusList>> getServices(String serviceId, String accept, ServerWebExchange exchange) throws Exception {
        return serviceController.getServices(serviceId, exchange)
                .map(responseEntity -> new ResponseEntity<>(serviceControllerMapper.toServiceStatusListV3(
                        responseEntity.getBody()), responseEntity.getStatusCode()))
                .doOnError(errorHandlingService::handleError);
    }

    @Override
    public Mono<ResponseEntity<Object>> keepAliveService(String serviceId, String accept, Mono<String> body, ServerWebExchange exchange) throws Exception {
        return serviceController.keepAliveService(serviceId, exchange);
    }

    @Override
    public Mono<ResponseEntity<Object>> putService(Mono<ServiceRegistrationInfo> serviceRegistrationInfo, ServerWebExchange exchange) throws Exception {
        return serviceController.putService(serviceRegistrationInfo.map(serviceControllerMapper::toServiceRegistrationInfoV2), exchange)
                .doOnError(errorHandlingService::handleError);
    }
}
