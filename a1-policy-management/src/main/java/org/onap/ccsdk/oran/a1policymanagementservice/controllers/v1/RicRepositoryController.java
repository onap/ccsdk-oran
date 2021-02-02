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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = Consts.V1_API_NAME)
public class RicRepositoryController {

    @Autowired
    private Rics rics;

    @Autowired
    PolicyTypes types;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    /**
     * Example: http://localhost:8081/rics?managedElementId=kista_1
     * 
     * @throws EntityNotFoundException
     */
    @GetMapping("/ric")
    @Operation(summary = "Returns the name of a RIC managing one Mananged Element")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Near-RT RIC is found", //
                    content = @Content(schema = @Schema(implementation = String.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })

    public ResponseEntity<String> getRic( //
            @Parameter(name = "managedElementId", required = true, description = "The identity of the Managed Element") //
            @RequestParam(name = "managedElementId", required = true) String managedElementId)
            throws EntityNotFoundException {
        Ric ric = this.rics.lookupRicForManagedElement(managedElementId);
        return new ResponseEntity<>(ric.id(), HttpStatus.OK);
    }

    /**
     * @return a Json array of all RIC data Example: http://localhost:8081/ric
     */
    @GetMapping("/rics")
    @Operation(summary = "Query Near-RT RIC information")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "OK", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RicInfo.class)))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy type is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })
    public ResponseEntity<String> getRics( //
            @Parameter(name = "policyType", required = false, description = "The name of the policy type") //
            @RequestParam(name = "policyType", required = false) String supportingPolicyType) {
        if ((supportingPolicyType != null) && (this.types.get(supportingPolicyType) == null)) {
            return new ResponseEntity<>("Policy type not found", HttpStatus.NOT_FOUND);
        }

        List<RicInfo> result = new ArrayList<>();
        for (Ric ric : rics.getRics()) {
            if (supportingPolicyType == null || ric.isSupportingType(supportingPolicyType)) {
                result.add(new RicInfo(ric.id(), ric.getManagedElementIds(), ric.getSupportedPolicyTypeNames(),
                        ric.getState().toString()));
            }
        }

        return new ResponseEntity<>(gson.toJson(result), HttpStatus.OK);
    }

}
