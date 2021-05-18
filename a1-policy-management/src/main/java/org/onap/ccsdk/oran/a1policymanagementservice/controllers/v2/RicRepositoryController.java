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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.InvalidRequestException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("RicRepositoryControllerV2")
@Tag(name = RicRepositoryController.API_NAME)
public class RicRepositoryController {

    public static final String API_NAME = "NearRT-RIC Repository";
    public static final String API_DESCRIPTION = "";

    @Autowired
    private Rics rics;

    @Autowired
    PolicyTypes types;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    private static final String GET_RIC_BRIEF = "Returns info for one Near-RT RIC";
    private static final String GET_RIC_DETAILS =
            "Either a Near-RT RIC identity or a Mananged Element identity can be specified.<br>" //
                    + "The intention with Mananged Element identity is the ID used in O1 for accessing the traffical element (such as the ID of CU).";

    /**
     * Example: http://localhost:8081/v2/rics/ric?managed_element_id=kista_1
     * 
     * @throws EntityNotFoundException
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/rics/ric", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = GET_RIC_BRIEF, description = GET_RIC_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Near-RT RIC is found", //
                    content = @Content(schema = @Schema(implementation = RicInfo.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getRic( //
            @Parameter(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = false,
                    description = "The identity of a Managed Element. If given, the Near-RT RIC managing the ME is returned.") //
            @RequestParam(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = false) String managedElementId,
            @Parameter(name = Consts.RIC_ID_PARAM, required = false,
                    description = "The identity of a Near-RT RIC to get information for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId)
            throws EntityNotFoundException, InvalidRequestException {
        if (managedElementId != null && ricId != null) {
            throw new InvalidRequestException("Give one query parameter");
        } else if (managedElementId != null) {
            var ric = this.rics.lookupRicForManagedElement(managedElementId);
            return new ResponseEntity<>(gson.toJson(toRicInfo(ric)), HttpStatus.OK);
        } else if (ricId != null) {
            var info = toRicInfo(this.rics.getRic(ricId));
            return new ResponseEntity<>(gson.toJson(info), HttpStatus.OK);
        } else {
            throw new InvalidRequestException("Give one query parameter");
        }
    }

    static final String QUERY_RIC_INFO_DETAILS =
            "The call returns all Near-RT RICs that supports a given policy type identity";

    /**
     * @return a Json array of all RIC data Example: http://localhost:8081/v2/ric
     * @throws EntityNotFoundException
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/rics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query Near-RT RIC information", description = QUERY_RIC_INFO_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "OK", //
                    content = @Content(schema = @Schema(implementation = RicInfoList.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy type is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getRics( //
            @Parameter(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    description = "The identity of a policy type. If given, all Near-RT RICs supporteing the policy type are returned") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String supportingPolicyType)
            throws EntityNotFoundException {
        if ((supportingPolicyType != null) && (this.types.get(supportingPolicyType) == null)) {
            throw new EntityNotFoundException("Policy type not found");
        }

        List<RicInfo> result = new ArrayList<>();
        for (var ric : rics.getRics()) {
            if (supportingPolicyType == null || ric.isSupportingType(supportingPolicyType)) {
                result.add(toRicInfo(ric));
            }
        }

        return new ResponseEntity<>(gson.toJson(new RicInfoList(result)), HttpStatus.OK);
    }

    private RicInfo.RicState toRicState(Ric.RicState state) {
        switch (state) {
            case AVAILABLE:
                return RicInfo.RicState.AVAILABLE;
            case CONSISTENCY_CHECK:
                return RicInfo.RicState.CONSISTENCY_CHECK;
            case SYNCHRONIZING:
                return RicInfo.RicState.SYNCHRONIZING;
            case UNAVAILABLE:
                return RicInfo.RicState.UNAVAILABLE;
            default:
                return RicInfo.RicState.UNAVAILABLE;
        }
    }

    private RicInfo toRicInfo(Ric ric) {
        return new RicInfo(ric.id(), ric.getManagedElementIds(), ric.getSupportedPolicyTypeNames(),
                toRicState(ric.getState()));
    }
}
