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

package org.onap.ccsdk.oran.a1policymanagementservice.util.v3;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

public class ReactiveEntryExitFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest httpRequest = exchange.getRequest();
        ServerHttpResponse httpResponse = exchange.getResponse();
        MultiValueMap<String, String> queryParams = httpRequest.getQueryParams();
        logger.info("Request received with path: {}, and the Request Id: {}, with HTTP Method: {}", httpRequest.getPath(),
                exchange.getRequest().getId(), exchange.getRequest().getMethod());
        if (!queryParams.isEmpty())
            logger.trace("For the request Id: {}, the Query parameters are: {}", exchange.getRequest().getId(), queryParams);

        ServerHttpRequestDecorator loggingServerHttpRequestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
            String requestBody;
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(dataBuffer -> {
                    requestBody = dataBuffer.toString(StandardCharsets.UTF_8); // Read the bytes from the DataBuffer
                    if (!(requestBody.isEmpty() && requestBody.isBlank()))
                        logger.trace("For the request ID: {} the received request body: {}", exchange.getRequest().getId(), requestBody);
                });
            }
        };
        ServerHttpResponseDecorator loggingServerHttpResponseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            String responseBody;
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Flux<DataBuffer> buffer = Flux.from(body);
                return super.writeWith(buffer.doOnNext(dataBuffer -> {
//                    try {
                        dataBuffer.toString(StandardCharsets.UTF_8);
                        logger.info("For the request ID: {} the Status code of the response: {}", exchange.getRequest().getId(), httpResponse.getStatusCode());
                        logger.trace("For the request ID: {} the response is: {} ", exchange.getRequest().getId(), responseBody);
//                    } catch (Exception e) {
//                        logger.info("For the request ID: {} we got an Error during processing: {}", exchange.getRequest().getId(), e.getMessage());
//                        logger.trace("For the request ID: {} the response body :: {}", exchange.getRequest().getId(), responseBody);
//                    }
                }));
            }
        };
        return chain.filter(exchange.mutate().request(loggingServerHttpRequestDecorator).response(loggingServerHttpResponseDecorator).build());
    }


}