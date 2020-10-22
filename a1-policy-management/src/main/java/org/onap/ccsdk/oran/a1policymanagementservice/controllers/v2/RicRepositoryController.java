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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
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
@Api(tags = {Consts.V2_API_NAME})
public class RicRepositoryController {

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
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/rics/ric", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = GET_RIC_BRIEF, notes = GET_RIC_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Near-RT RIC is found", response = RicInfo.class), //
            @ApiResponse(code = 404, message = "Near-RT RIC is not found", response = ErrorResponse.ErrorInfo.class) //
    })
    public ResponseEntity<Object> getRic( //
            @ApiParam(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = false,
                    value = "The identity of a Managed Element. If given, the Near-RT RIC managing the ME is returned.") //
            @RequestParam(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = false) String managedElementId,
            @ApiParam(name = Consts.RIC_ID_PARAM, required = false,
                    value = "The identity of a Near-RT RIC to get information for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId) {
        try {
            if (managedElementId != null && ricId != null) {
                return ErrorResponse.create("Give one query parameter", HttpStatus.BAD_REQUEST);
            } else if (managedElementId != null) {
                Optional<Ric> ric = this.rics.lookupRicForManagedElement(managedElementId);
                if (ric.isPresent()) {
                    return new ResponseEntity<>(gson.toJson(toRicInfo(ric.get())), HttpStatus.OK);
                } else {
                    return ErrorResponse.create("No Near-RT RIC managing the ME is found", HttpStatus.NOT_FOUND);
                }
            } else if (ricId != null) {
                RicInfo info = toRicInfo(this.rics.getRic(ricId));
                return new ResponseEntity<>(gson.toJson(info), HttpStatus.OK);
            } else {
                return ErrorResponse.create("Give one query parameter", HttpStatus.BAD_REQUEST);
            }
        } catch (ServiceException e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    static final String QUERY_RIC_INFO_DETAILS =
            "The call returns all Near-RT RICs that supports a given policy type identity";

    /**
     * @return a Json array of all RIC data Example: http://localhost:8081/v2/ric
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/rics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Query Near-RT RIC information", notes = QUERY_RIC_INFO_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "OK", response = RicInfoList.class), //
            @ApiResponse(code = 404, message = "Policy type is not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getRics( //
            @ApiParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    value = "The identity of a policy type. If given, all Near-RT RICs supporteing the policy type are returned") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String supportingPolicyType

    ) {
        if ((supportingPolicyType != null) && (this.types.get(supportingPolicyType) == null)) {
            return ErrorResponse.create("Policy type not found", HttpStatus.NOT_FOUND);
        }

        List<RicInfo> result = new ArrayList<>();
        for (Ric ric : rics.getRics()) {
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
