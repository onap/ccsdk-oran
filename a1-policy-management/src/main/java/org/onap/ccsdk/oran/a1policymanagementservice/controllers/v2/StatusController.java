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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.immutables.gson.Gson;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController("StatusControllerV2")
@Tag(name = StatusController.API_NAME)
public class StatusController {

    public static final String API_NAME = "Health Check";
    public static final String API_DESCRIPTION = "";

    @Gson.TypeAdapters
    @Schema(name = "status_info_v2")
    class StatusInfo {
        @Schema(description = "status text")
        public final String status;

        StatusInfo(String status) {
            this.status = status;
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns status and statistics of this service")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Service is living", //
                    content = @Content(schema = @Schema(implementation = StatusInfo.class))), //
    })

    public Mono<ResponseEntity<Object>> getStatus() {
        StatusInfo info = new StatusInfo("hunky dory");
        return Mono.just(new ResponseEntity<>(info, HttpStatus.OK));
    }

    @GetMapping("/status")
    @Operation(summary = "Returns status and statistics of this service")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", description = "Service is living",
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })

    public Mono<ResponseEntity<String>> getStatusV1() {
        return Mono.just(new ResponseEntity<>("hunky dory", HttpStatus.OK));
    }

}
