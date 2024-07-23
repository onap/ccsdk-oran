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
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v3.NearRtRicRepositoryApi;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.mappers.v3.RicRepositoryMapper;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.RicRepositoryController;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.RicInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.RicInfoList;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.ErrorHandlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("RicRepositoryControllerV3")
@Tag(
        name = RicRepositoryControllerV3.API_NAME,
        description = RicRepositoryControllerV3.API_DESCRIPTION
)
@RequestMapping(Consts.V3_API_ROOT)
public class RicRepositoryControllerV3 implements NearRtRicRepositoryApi {

    public static final String API_NAME = "NearRT-RIC Repository V3";
    public static final String API_DESCRIPTION = "API used to get the NearRT-RIC for the managed element";
    @Autowired
    private RicRepositoryController ricRepositoryController;

    @Autowired
    private RicRepositoryMapper ricRepositoryMapper;

    @Autowired
    ErrorHandlingService errorHandlingService;

    @Override
    public Mono<ResponseEntity<RicInfo>> getRic(String managedElementId, String ricId, String accept, ServerWebExchange exchange) throws Exception {
        return ricRepositoryController.getRic(managedElementId, ricId, exchange)
                .map(responseEntity -> new ResponseEntity<>(ricRepositoryMapper.toRicInfoV3(responseEntity.getBody()), responseEntity.getStatusCode()))
                .doOnError(error -> errorHandlingService.handleError(error));
    }

    @Override
    public Mono<ResponseEntity<RicInfoList>> getRics(String policyTypeId, String accept, ServerWebExchange exchange) throws Exception {
        return ricRepositoryController.getRics(policyTypeId, exchange)
                .map(responseEntity -> new ResponseEntity<>(ricRepositoryMapper.toRicInfoListV3(responseEntity.getBody()), responseEntity.getStatusCode()))
                .doOnError(error -> errorHandlingService.handleError(error));
    }
}
