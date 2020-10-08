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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

/**
 * Generic reactive REST client.
 */
public class AsyncRestClient {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WebClient webClient = null;
    private final String baseUrl;
    private static final AtomicInteger sequenceNumber = new AtomicInteger();
    private final SslContext sslContext;

    /**
     * Note that only http (not https) will work when this constructor is used.
     * 
     * @param baseUrl
     */
    public AsyncRestClient(String baseUrl) {
        this(baseUrl, null);
    }

    public AsyncRestClient(String baseUrl, SslContext sslContext) {
        this.baseUrl = baseUrl;
        this.sslContext = sslContext;
    }

    public Mono<ResponseEntity<String>> postForEntity(String uri, @Nullable String body) {
        Object traceTag = createTraceTag();
        logger.debug("{} POST uri = '{}{}''", traceTag, baseUrl, uri);
        logger.trace("{} POST body: {}", traceTag, body);
        Mono<String> bodyProducer = body != null ? Mono.just(body) : Mono.empty();
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.post() //
                            .uri(uri) //
                            .contentType(MediaType.APPLICATION_JSON) //
                            .body(bodyProducer, String.class);
                    return retrieve(traceTag, request);
                });
    }

    public Mono<String> post(String uri, @Nullable String body) {
        return postForEntity(uri, body) //
                .flatMap(this::toBody);
    }

    public Mono<String> postWithAuthHeader(String uri, String body, String username, String password) {
        Object traceTag = createTraceTag();
        logger.debug("{} POST (auth) uri = '{}{}''", traceTag, baseUrl, uri);
        logger.trace("{} POST body: {}", traceTag, body);
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.post() //
                            .uri(uri) //
                            .headers(headers -> headers.setBasicAuth(username, password)) //
                            .contentType(MediaType.APPLICATION_JSON) //
                            .bodyValue(body);
                    return retrieve(traceTag, request) //
                            .flatMap(this::toBody);
                });
    }

    public Mono<ResponseEntity<String>> putForEntity(String uri, String body) {
        Object traceTag = createTraceTag();
        logger.debug("{} PUT uri = '{}{}''", traceTag, baseUrl, uri);
        logger.trace("{} PUT body: {}", traceTag, body);
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.put() //
                            .uri(uri) //
                            .contentType(MediaType.APPLICATION_JSON) //
                            .bodyValue(body);
                    return retrieve(traceTag, request);
                });
    }

    public Mono<ResponseEntity<String>> putForEntity(String uri) {
        Object traceTag = createTraceTag();
        logger.debug("{} PUT uri = '{}{}''", traceTag, baseUrl, uri);
        logger.trace("{} PUT body: <empty>", traceTag);
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.put() //
                            .uri(uri);
                    return retrieve(traceTag, request);
                });
    }

    public Mono<String> put(String uri, String body) {
        return putForEntity(uri, body) //
                .flatMap(this::toBody);
    }

    public Mono<ResponseEntity<String>> getForEntity(String uri) {
        Object traceTag = createTraceTag();
        logger.debug("{} GET uri = '{}{}''", traceTag, baseUrl, uri);
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.get().uri(uri);
                    return retrieve(traceTag, request);
                });
    }

    public Mono<String> get(String uri) {
        return getForEntity(uri) //
                .flatMap(this::toBody);
    }

    public Mono<ResponseEntity<String>> deleteForEntity(String uri) {
        Object traceTag = createTraceTag();
        logger.debug("{} DELETE uri = '{}{}''", traceTag, baseUrl, uri);
        return getWebClient() //
                .flatMap(client -> {
                    RequestHeadersSpec<?> request = client.delete().uri(uri);
                    return retrieve(traceTag, request);
                });
    }

    public Mono<String> delete(String uri) {
        return deleteForEntity(uri) //
                .flatMap(this::toBody);
    }

    private Mono<ResponseEntity<String>> retrieve(Object traceTag, RequestHeadersSpec<?> request) {
        final Class<String> clazz = String.class;
        return request.retrieve() //
                .toEntity(clazz) //
                .doOnNext(entity -> logReceivedData(traceTag, entity)) //
                .doOnError(throwable -> onHttpError(traceTag, throwable));
    }

    private void logReceivedData(Object traceTag, ResponseEntity<String> entity) {
        logger.trace("{} Received: {} {}", traceTag, entity.getBody(), entity.getHeaders().getContentType());
    }

    private static Object createTraceTag() {
        return sequenceNumber.incrementAndGet();
    }

    private void onHttpError(Object traceTag, Throwable t) {
        if (t instanceof WebClientResponseException) {
            WebClientResponseException exception = (WebClientResponseException) t;
            logger.debug("{} HTTP error status = '{}', body '{}'", traceTag, exception.getStatusCode(),
                    exception.getResponseBodyAsString());
        } else {
            logger.debug("{} HTTP error", traceTag, t);
        }
    }

    private Mono<String> toBody(ResponseEntity<String> entity) {
        if (entity.getBody() == null) {
            return Mono.just("");
        } else {
            return Mono.just(entity.getBody());
        }
    }

    private TcpClient createTcpClientSecure(SslContext sslContext) {
        return TcpClient.create(ConnectionProvider.newConnection()) //
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) //
                .secure(c -> c.sslContext(sslContext)) //
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(30));
                    connection.addHandlerLast(new WriteTimeoutHandler(30));
                });
    }

    private TcpClient createTcpClientInsecure() {
        return TcpClient.create(ConnectionProvider.newConnection()) //
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) //
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(30));
                    connection.addHandlerLast(new WriteTimeoutHandler(30));
                });
    }

    private WebClient createWebClient(String baseUrl, TcpClient tcpClient) {
        HttpClient httpClient = HttpClient.from(tcpClient);
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder() //
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) //
                .build();
        return WebClient.builder() //
                .clientConnector(connector) //
                .baseUrl(baseUrl) //
                .exchangeStrategies(exchangeStrategies) //
                .build();
    }

    private Mono<WebClient> getWebClient() {
        if (this.webClient == null) {
            try {
                if (this.sslContext != null) {
                    TcpClient tcpClient = createTcpClientSecure(sslContext);
                    this.webClient = createWebClient(this.baseUrl, tcpClient);
                } else {
                    TcpClient tcpClient = createTcpClientInsecure();
                    this.webClient = createWebClient(this.baseUrl, tcpClient);
                }
            } catch (Exception e) {
                logger.error("Could not create WebClient {}", e.getMessage());
                return Mono.error(e);
            }
        }
        return Mono.just(this.webClient);
    }

}
