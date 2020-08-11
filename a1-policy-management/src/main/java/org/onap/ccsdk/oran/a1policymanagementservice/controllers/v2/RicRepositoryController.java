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
@Api(tags = Consts.V2_API_NAME)
public class RicRepositoryController {

    @Autowired
    private Rics rics;

    @Autowired
    PolicyTypes types;

    private static Gson gson = new GsonBuilder() //
        .serializeNulls() //
        .create(); //

    /**
     * Example: http://localhost:8081/ric?managed_element_id=kista_1
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/ric", produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation(value = "Returns the name of a RIC managing one Mananged Element")
    @ApiResponses(
        value = { //
            @ApiResponse(code = 200, message = "RIC is found", response = String.class), //
            @ApiResponse(code = 404, message = "RIC is not found", response = ErrorResponse.ErrorInfo.class) //
        })
    public ResponseEntity<Object> getRic( //
        @ApiParam(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = true, value = "The ID of the Managed Element") //
        @RequestParam(name = Consts.MANAGED_ELEMENT_ID_PARAM, required = true) String managedElementId) {
        Optional<Ric> ric = this.rics.lookupRicForManagedElement(managedElementId);

        if (ric.isPresent()) {
            return new ResponseEntity<>(ric.get().id(), HttpStatus.OK);
        } else {
            return ErrorResponse.create("No RIC found", HttpStatus.NOT_FOUND);
        }
    }

    /**
     * @return a Json array of all RIC data Example: http://localhost:8081/ric
     */
    @GetMapping(path = Consts.V2_API_ROOT + "/rics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Query Near-RT RIC information")
    @ApiResponses(
        value = { //
            @ApiResponse(code = 200, message = "OK", response = RicInfoList.class), //
            @ApiResponse(code = 404, message = "Policy type is not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getRics( //
        @ApiParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false, value = "The name of the policy type") //
        @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String supportingPolicyType) {
        if ((supportingPolicyType != null) && (this.types.get(supportingPolicyType) == null)) {
            return ErrorResponse.create("Policy type not found", HttpStatus.NOT_FOUND);
        }

        List<RicInfo> result = new ArrayList<>();
        for (Ric ric : rics.getRics()) {
            if (supportingPolicyType == null || ric.isSupportingType(supportingPolicyType)) {
                result.add(new RicInfo(ric.id(), ric.getManagedElementIds(), ric.getSupportedPolicyTypeNames(),
                    ric.getState().toString()));
            }
        }

        return new ResponseEntity<>(gson.toJson(new RicInfoList(result)), HttpStatus.OK);
    }

}
