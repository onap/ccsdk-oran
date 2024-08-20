/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2023 Nordix Foundation. All rights reserved.
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

import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v2.HealthCheckApi;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.StatusInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("statusControllerV2")
@Tag(   name = StatusController.API_NAME,
        description = StatusController.API_DESCRIPTION
)
public class StatusController implements HealthCheckApi{

    public static final String API_NAME = "Health Check";
    public static final String API_DESCRIPTION = "";

    @Override
    public Mono<ResponseEntity<StatusInfo>> getStatus(final ServerWebExchange exchange) {
        StatusInfo info = new StatusInfo().status("success");
        return Mono.just(new ResponseEntity<>(info, HttpStatus.OK));
    }

    @Override
    public Mono<ResponseEntity<String>> getStatusV1(final ServerWebExchange exchange) {
        return Mono.just(new ResponseEntity<>("success", HttpStatus.OK));
    }

}