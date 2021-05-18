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

package org.onap.ccsdk.oran.a1policymanagementservice.dmaap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.dmaap.DmaapRequestMessage.Operation;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * The class handles incoming requests from DMAAP.
 * <p>
 * That means: invoke a REST call towards this services and to send back a
 * response though DMAAP
 */
public class DmaapMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(DmaapMessageHandler.class);
    private static Gson gson = new GsonBuilder().create();
    private final AsyncRestClient dmaapClient;
    private final AsyncRestClient pmsClient;

    public DmaapMessageHandler(AsyncRestClient dmaapClient, AsyncRestClient pmsClient) {
        this.pmsClient = pmsClient;
        this.dmaapClient = dmaapClient;
    }

    public Mono<String> handleDmaapMsg(DmaapRequestMessage dmaapRequestMessage) {
        return this.invokePolicyManagementService(dmaapRequestMessage) //
                .onErrorResume(t -> handlePolicyManagementServiceCallError(t, dmaapRequestMessage)) //
                .flatMap(response -> sendDmaapResponse(response.getBody(), dmaapRequestMessage,
                        response.getStatusCode()))
                .doOnError(t -> logger.warn("Failed to handle DMAAP message : {}", t.getMessage()))//
                .onErrorResume(t -> Mono.empty());
    }

    private Mono<ResponseEntity<String>> handlePolicyManagementServiceCallError(Throwable error,
            DmaapRequestMessage dmaapRequestMessage) {
        logger.debug("Policy Management Service call failed: {}", error.getMessage());
        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        var errorMessage = error.getMessage();
        if (error instanceof WebClientResponseException) {
            var exception = (WebClientResponseException) error;
            status = exception.getStatusCode();
            errorMessage = exception.getResponseBodyAsString();
        } else if (error instanceof ServiceException) {
            status = HttpStatus.BAD_REQUEST;
            errorMessage = error.getMessage();
        } else if (!(error instanceof WebClientException)) {
            logger.warn("Unexpected exception ", error);
        }
        return sendDmaapResponse(errorMessage, dmaapRequestMessage, status) //
                .flatMap(notUsed -> Mono.empty());
    }

    public Mono<String> sendDmaapResponse(String response, DmaapRequestMessage dmaapRequestMessage, HttpStatus status) {
        return createDmaapResponseMessage(dmaapRequestMessage, response, status) //
                .flatMap(this::sendToDmaap) //
                .onErrorResume(this::handleResponseCallError);
    }

    private Mono<ResponseEntity<String>> invokePolicyManagementService(DmaapRequestMessage dmaapRequestMessage) {
        var operation = dmaapRequestMessage.getOperation();
        var uri = dmaapRequestMessage.getUrl();

        if (operation == Operation.DELETE) {
            return pmsClient.deleteForEntity(uri);
        } else if (operation == Operation.GET) {
            return pmsClient.getForEntity(uri);
        } else if (operation == Operation.PUT) {
            return pmsClient.putForEntity(uri, payload(dmaapRequestMessage));
        } else if (operation == Operation.POST) {
            return pmsClient.postForEntity(uri, payload(dmaapRequestMessage));
        } else {
            return Mono.error(new ServiceException("Not implemented operation: " + operation));
        }
    }

    private String payload(DmaapRequestMessage message) {
        var payload = message.getPayload();
        if (payload != null) {
            return gson.toJson(payload);
        } else {
            logger.warn("Expected payload in message from DMAAP: {}", message);
            return "";
        }
    }

    private Mono<String> sendToDmaap(String body) {
        logger.debug("sendToDmaap: {} ", body);
        return dmaapClient.post("", "[" + body + "]");
    }

    private Mono<String> handleResponseCallError(Throwable t) {
        logger.warn("Failed to send response to DMaaP: {}", t.getMessage());
        return Mono.empty();
    }

    private Mono<String> createDmaapResponseMessage(DmaapRequestMessage dmaapRequestMessage, String response,
            HttpStatus status) {
        DmaapResponseMessage dmaapResponseMessage = DmaapResponseMessage.builder() //
                .status(status.toString()) //
                .message(response == null ? "" : response) //
                .type("response") //
                .correlationId(
                        dmaapRequestMessage.getCorrelationId() == null ? "" : dmaapRequestMessage.getCorrelationId()) //
                .originatorId(
                        dmaapRequestMessage.getOriginatorId() == null ? "" : dmaapRequestMessage.getOriginatorId()) //
                .requestId(dmaapRequestMessage.getRequestId() == null ? "" : dmaapRequestMessage.getRequestId()) //
                .timestamp(dmaapRequestMessage.getTimestamp() == null ? "" : dmaapRequestMessage.getTimestamp()) //
                .build();
        var str = gson.toJson(dmaapResponseMessage);
        return Mono.just(str);
    }
}
