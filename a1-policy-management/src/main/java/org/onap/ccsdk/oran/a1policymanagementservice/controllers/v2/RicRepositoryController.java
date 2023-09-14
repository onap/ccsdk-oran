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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v2.NearRtRicRepositoryApi;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.InvalidRequestException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.RicInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.RicInfoList;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RestController("RicRepositoryControllerV2")
@Tag( //
        name = RicRepositoryController.API_NAME, //
        description = RicRepositoryController.API_DESCRIPTION //
)
public class RicRepositoryController implements NearRtRicRepositoryApi {

    public static final String API_NAME = "NearRT-RIC Repository";
    public static final String API_DESCRIPTION = "";

    @Autowired
    private Rics rics;

    @Autowired
    PolicyTypes types;

    @Autowired
    ObjectMapper objectMapper;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    private static final String GET_RIC_BRIEF = "Returns info for one Near-RT RIC";
    private static final String GET_RIC_DETAILS =
            "Either a Near-RT RIC identity or a Managed Element identity can be specified.<br>" //
                    + "The intention with Managed Element identity is the ID used in O1 for accessing the traffical element (such as the ID of CU).";
    @Override
    public Mono<ResponseEntity<Object>> getRic(
            final String managedElementId, final String ricId, final ServerWebExchange exchange)
            throws Exception {
        if (managedElementId != null && ricId != null) {
            throw new InvalidRequestException("Give one query parameter");
        } else if (managedElementId != null) {
            Ric ric = this.rics.lookupRicForManagedElement(managedElementId);
            return Mono.just(new ResponseEntity<>(objectMapper.writeValueAsString(toRicInfo(ric)), HttpStatus.OK));
        } else if (ricId != null) {
            RicInfo info = toRicInfo(this.rics.getRic(ricId));
            return Mono.just(new ResponseEntity<>(objectMapper.writeValueAsString(info), HttpStatus.OK));
        } else {
            throw new InvalidRequestException("Give one query parameter");
        }
    }

    static final String QUERY_RIC_INFO_DETAILS =
            "The call returns all Near-RT RICs that supports a given policy type identity";

    @Override
    public Mono<ResponseEntity<Object>> getRics(final String supportingPolicyType, final ServerWebExchange exchange)
            throws Exception {
        if ((supportingPolicyType != null) && (this.types.get(supportingPolicyType) == null)) {
            throw new EntityNotFoundException("Policy type not found");
        }

        List<RicInfo> result = new ArrayList<>();
        for (Ric ric : rics.getRics()) {
            if (supportingPolicyType == null || ric.isSupportingType(supportingPolicyType)) {
                result.add(toRicInfo(ric));
            }
        }

        return Mono.just(new ResponseEntity<>(gson.toJson(new RicInfoList().rics(result)), HttpStatus.OK));
    }

    private RicInfo.StateEnum toRicState(Ric.RicState state) {
        switch (state) {
            case AVAILABLE:
                return RicInfo.StateEnum.AVAILABLE;
            case CONSISTENCY_CHECK:
                return RicInfo.StateEnum.CONSISTENCY_CHECK;
            case SYNCHRONIZING:
                return RicInfo.StateEnum.SYNCHRONIZING;
            case UNAVAILABLE:
                return RicInfo.StateEnum.UNAVAILABLE;
            default:
                return RicInfo.StateEnum.UNAVAILABLE;
        }
    }

    private RicInfo toRicInfo(Ric ric) {
        return new RicInfo().ricId(ric.id())
                .managedElementIds((List<String>) ric.getManagedElementIds())
                .policytypeIds((List<String>) ric.getSupportedPolicyTypeNames())
                .state(toRicState(ric.getState()));
    }
}
