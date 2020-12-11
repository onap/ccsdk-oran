/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020 Nordix Foundation. All rights reserved.
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
import com.google.gson.annotations.SerializedName;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public class ErrorResponse {
    private static Gson gson = new GsonBuilder() //
            .create(); //

    // Returned as body for all failed REST calls
    @ApiModel(value = "error_information", description = "Problem as defined in https://tools.ietf.org/html/rfc7807")
    public static class ErrorInfo {
        @SerializedName("type")
        private String type = "about:blank";

        @SerializedName("title")
        private String title = null;

        @SerializedName("status")
        private final Integer status;

        @SerializedName("detail")
        private String detail = null;

        @SerializedName("instance")
        private String instance = null;

        public ErrorInfo(String detail, Integer status) {
            this.detail = detail;
            this.status = status;
        }

        @ApiModelProperty(example = "503",
                value = "The HTTP status code generated by the origin server for this occurrence of the problem. ")
        public Integer getStatus() {
            return status;
        }

        @ApiModelProperty(example = "Policy type not found",
                value = " A human-readable explanation specific to this occurrence of the problem.")
        public String getDetail() {
            return this.detail;
        }

    }

    @ApiModelProperty(value = "message")
    public final String message;

    ErrorResponse(String message) {
        this.message = message;
    }

    static Mono<ResponseEntity<Object>> createMono(String text, HttpStatus code) {
        return Mono.just(create(text, code));
    }

    static Mono<ResponseEntity<Object>> createMono(Exception e, HttpStatus code) {
        return createMono(e.toString(), code);
    }

    static ResponseEntity<Object> create(String text, HttpStatus code) {
        ErrorInfo p = new ErrorInfo(text, code.value());
        String json = gson.toJson(p);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(json, headers, code);
    }

    public static ResponseEntity<Object> create(Exception e, HttpStatus code) {
        return create(e.toString(), code);
    }

}
