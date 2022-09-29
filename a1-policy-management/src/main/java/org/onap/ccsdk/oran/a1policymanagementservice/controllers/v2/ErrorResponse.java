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

import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.invoke.MethodHandles;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public class ErrorResponse {
    private static Gson gson = new GsonBuilder() //
            .create(); //
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Returned as body for all failed REST calls
    @Schema(name = "error_information", description = "Problem as defined in https://tools.ietf.org/html/rfc7807")
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

        @Schema(example = "404",
                description = "The HTTP status code generated by the origin server for this occurrence of the problem. ")
        public Integer getStatus() {
            return status;
        }

        @Schema(example = "Policy type not found",
                description = " A human-readable explanation specific to this occurrence of the problem.")
        public String getDetail() {
            return this.detail;
        }

    }

    @Schema(name = "message", description = "message")
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
        logger.debug("Error response: {}, {}", code, text);
        ErrorInfo p = new ErrorInfo(text, code.value());
        String json = gson.toJson(p);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(json, headers, code);
    }

    public static ResponseEntity<Object> create(Throwable e, HttpStatus code) {
        if (e instanceof RuntimeException) {
            code = HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (e instanceof ServiceException) {
            ServiceException se = (ServiceException) e;
            if (se.getHttpStatus() != null) {
                code = se.getHttpStatus();
            }
        }
        return create(e.toString(), code);
    }

}
