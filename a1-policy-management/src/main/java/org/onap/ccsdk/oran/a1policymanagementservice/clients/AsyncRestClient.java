/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2022 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.lang.invoke.MethodHandles;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig.HttpProxyConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * Generic reactive REST client.
 */
public class AsyncRestClient {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WebClient webClient = null;
    private final String baseUrl;
    private final SslContext sslContext;
    private final HttpProxyConfig httpProxyConfig;
    private final SecurityContext securityContext;

    public AsyncRestClient(String baseUrl, @Nullable SslContext sslContext, @Nullable HttpProxyConfig httpProxyConfig,
            SecurityContext securityContext) {
        this.baseUrl = baseUrl;
        this.sslContext = sslContext;
        this.httpProxyConfig = httpProxyConfig;
        this.securityContext = securityContext;
    }

    public Mono<ResponseEntity<String>> postForEntity(String uri, @Nullable String body) {
        Mono<String> bodyProducer = body != null ? Mono.just(body) : Mono.empty();

        RequestHeadersSpec<?> request = getWebClient() //
                .post() //
                .uri(uri) //
                .contentType(MediaType.APPLICATION_JSON) //
                .body(bodyProducer, String.class);
        return retrieve(request);
    }

    public Mono<String> post(String uri, @Nullable String body) {
        return postForEntity(uri, body) //
                .map(this::toBody);
    }

    public Mono<String> postWithAuthHeader(String uri, String body, String username, String password) {
        RequestHeadersSpec<?> request = getWebClient() //
                .post() //
                .uri(uri) //
                .headers(headers -> headers.setBasicAuth(username, password)) //
                .contentType(MediaType.APPLICATION_JSON) //
                .bodyValue(body);
        return retrieve(request) //
                .map(this::toBody);
    }

    public Mono<ResponseEntity<String>> postWithToken(String uri, String body, String token) {
        RequestHeadersSpec<?> request = getWebClient() //
                .post() //
                .uri(uri) //
                .headers(headers -> headers.setBearerAuth(token)) //
                .contentType(MediaType.APPLICATION_JSON) //
                .bodyValue(body);
        return retrieve(request);
    }

    public Mono<ResponseEntity<String>> putForEntity(String uri, String body) {
        RequestHeadersSpec<?> request = getWebClient() //
                .put() //
                .uri(uri) //
                .contentType(MediaType.APPLICATION_JSON) //
                .bodyValue(body);
        return retrieve(request);
    }

    public Mono<ResponseEntity<String>> putForEntity(String uri) {
        RequestHeadersSpec<?> request = getWebClient() //
                .put() //
                .uri(uri);
        return retrieve(request);
    }

    public Mono<String> put(String uri, String body) {
        return putForEntity(uri, body) //
                .map(this::toBody);
    }

    public Mono<ResponseEntity<String>> getForEntity(String uri) {
        RequestHeadersSpec<?> request = getWebClient().get().uri(uri);
        return retrieve(request);
    }

    public Mono<String> get(String uri) {
        return getForEntity(uri) //
                .map(this::toBody);
    }

    public Mono<ResponseEntity<String>> deleteForEntity(String uri) {
        RequestHeadersSpec<?> request = getWebClient().delete().uri(uri);
        return retrieve(request);
    }

    public Mono<String> delete(String uri) {
        return deleteForEntity(uri) //
                .map(this::toBody);
    }

    private Mono<ResponseEntity<String>> retrieve(RequestHeadersSpec<?> request) {
        if (securityContext.isConfigured()) {
            request.headers(h -> h.setBearerAuth(securityContext.getBearerAuthToken()));
        }
        return request.retrieve() //
                .toEntity(String.class) //
                .doOnError(this::onError);

    }

    private void onError(Throwable t) {
        if (t instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) t;
            logger.debug("Response error: {}", e.getResponseBodyAsString());
        }
    }

    private String toBody(ResponseEntity<String> entity) {
        if (entity.getBody() == null) {
            return "";
        } else {
            return entity.getBody();
        }
    }

    private boolean isHttpProxyConfigured() {
        return httpProxyConfig != null && httpProxyConfig.getHttpProxyPort() > 0
                && !httpProxyConfig.getHttpProxyHost().isEmpty();
    }

    private HttpClient buildHttpClient() {
        HttpClient httpClient = HttpClient.create() //
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) //
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(30));
                    connection.addHandlerLast(new WriteTimeoutHandler(30));
                });

        if (this.sslContext != null) {
            httpClient = httpClient.secure(ssl -> ssl.sslContext(sslContext));
        }

        if (isHttpProxyConfigured()) {
            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)
                    .host(httpProxyConfig.getHttpProxyHost()).port(httpProxyConfig.getHttpProxyPort()));
        }
        return httpClient;
    }

    public WebClient buildWebClient(String baseUrl) {
        final HttpClient httpClient = buildHttpClient();
        return WebClientUtil.buildWebClient(baseUrl, httpClient);
    }

    private WebClient getWebClient() {
        if (this.webClient == null) {
            this.webClient = buildWebClient(baseUrl);
        }
        return this.webClient;
    }
}
