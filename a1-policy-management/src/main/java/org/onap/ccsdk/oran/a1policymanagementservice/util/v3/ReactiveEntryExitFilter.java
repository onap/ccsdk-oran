/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.util.v3;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class ReactiveEntryExitFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FACILITY_KEY = "facility";
    private static final String SUBJECT_KEY = "subject";

    private final List<PathPattern> excludedPatterns = new ArrayList<>();

    public ReactiveEntryExitFilter excludePathPatterns(PathPattern pattern) {
        excludedPatterns.add(pattern);
        return this;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Skip processing for excluded patterns
        for (PathPattern pattern : excludedPatterns) {
            if (pattern.matches(exchange.getRequest().getPath().pathWithinApplication())) {
                return chain.filter(exchange);
            }
        }

        // sets FACILITY_KEY and SUBJECT_KEY in MDC
        auditLog(exchange.getRequest());

        String subject = MDC.get(SUBJECT_KEY);
        String facility = MDC.get(FACILITY_KEY);

        ServerHttpRequest httpRequest = exchange.getRequest();
        MultiValueMap<String, String> queryParams = httpRequest.getQueryParams();
        logger.info("Request received with path: {}, and the Request Id: {}, with HTTP Method: {}", httpRequest.getPath(),
                exchange.getRequest().getId(), exchange.getRequest().getMethod());
        if (!queryParams.isEmpty())
            logger.trace("For the request Id: {}, the Query parameters are: {}", exchange.getRequest().getId(), queryParams);

        ServerHttpRequestDecorator loggingServerHttpRequestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
            String requestBody;

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.deferContextual(contextView ->
                    // Now, return the original body flux with a doFinally to clear MDC after processing
                    super.getBody().doOnNext(dataBuffer -> {
                        requestBody = dataBuffer.toString(StandardCharsets.UTF_8); // Read the bytes from the DataBuffer
                        logger.trace("For the request ID: {} the received request body: {}", exchange.getRequest().getId(), requestBody);
                    }).doOnEach(signal -> MDC.clear())
                            .doFinally(signal -> MDC.clear())); // Clear MDC after the request body is processed
            }
        };

        StringBuilder responseBodyBuilder = new StringBuilder();

        ServerHttpResponseDecorator loggingServerHttpResponseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return Mono.deferContextual(contextView ->
                    super.writeWith(Flux.from(body).doOnNext(dataBuffer -> {
                        String responseBody = dataBuffer.toString(StandardCharsets.UTF_8);
                        responseBodyBuilder.append(responseBody);
                    })).doFinally(signalType -> {
                        restoreFromContextToMdc(contextView);
                        logger.info("For the request ID: {} the Status code of the response: {}",
                                exchange.getRequest().getId(), exchange.getResponse().getStatusCode());
                        logger.trace("For the request ID: {} the response is: {} ",
                                exchange.getRequest().getId(), responseBodyBuilder);
                        MDC.clear();
                    })); // Clear MDC to prevent leakage
            }
        };
        return chain.filter(exchange.mutate()
                        .request(loggingServerHttpRequestDecorator)
                        .response(loggingServerHttpResponseDecorator)
                        .build())
                .contextWrite(Context.of(FACILITY_KEY, facility, SUBJECT_KEY, subject))
                .doFinally(signalType -> MDC.clear());
    }

    private void auditLog(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        String subject = "n/av";
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            subject = this.getSubjectFromToken(token);
        }

        String facility = "log audit";

        MDC.put(SUBJECT_KEY, subject);
        MDC.put(FACILITY_KEY, facility);
    }

    private String getSubjectFromToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2) {
                logger.warn("Invalid JWT: Missing payload");
                return "n/av";
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();

            if (jsonObject.has("upn")) {
                return sanitize(jsonObject.get("upn").getAsString());
            } else if (jsonObject.has("preferred_username")) {
                return sanitize(jsonObject.get("preferred_username").getAsString());
            } else if (jsonObject.has("sub")) {
                return sanitize(jsonObject.get("sub").getAsString());
            }
        } catch (Exception e) {
            logger.warn("Failed to extract subject from token: {}", e.getMessage());
        }
        return "n/av";
    }

    private String sanitize(String input) {
        if (input == null) {
            return "n/av";
        }
        // Replace dangerous characters
        return input.replaceAll("[\r\n]", "")  // Remove newlines
                .replaceAll("[{}()<>]", "") // Remove brackets
                .replaceAll("[^\\p{Alnum}\\p{Punct}\\s]", ""); // Remove non-printable characters
    }

    private void restoreFromContextToMdc(ContextView context) {
        context.getOrEmpty(FACILITY_KEY).ifPresent(value -> MDC.put(FACILITY_KEY, value.toString()));
        context.getOrEmpty(SUBJECT_KEY).ifPresent(value -> MDC.put(SUBJECT_KEY, value.toString()));
    }
}