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

package org.onap.ccsdk.oran.a1policymanagementservice.service.v3;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;

@Service
public class ErrorHandlingService {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public Mono<ResponseEntity<ProblemDetails>> handleError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            return createErrorResponse(e.getResponseBodyAsString(), e.getStatusCode());
        } else if (throwable instanceof WebClientException) {
            WebClientException e = (WebClientException) throwable;
            return createErrorResponse(e.getMessage(), HttpStatus.BAD_GATEWAY);
        } else if (throwable instanceof ServiceException) {
            ServiceException e = (ServiceException) throwable;
            return createErrorResponse(e.getMessage(), e.getHttpStatus());
        } else {
            return createErrorResponse(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Mono<ResponseEntity<ProblemDetails>> createErrorResponse(String errorBody, HttpStatusCode statusCode) {
        logger.debug("Error content: {}, with status code {}", errorBody, statusCode);
        ProblemDetails problemDetail = new ProblemDetails().type("about:blank");
        problemDetail.setDetail(errorBody);
        problemDetail.setStatus(new BigDecimal(statusCode.value()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return Mono.just(new ResponseEntity<>(problemDetail, headers, statusCode));
    }
}
