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
package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@DependsOn({"otelConfig"})
public class WebClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static OtelConfig otelConfig;

    private static SpringWebfluxTelemetry springWebfluxTelemetry;

    WebClientUtil(OtelConfig otelConfig, @Autowired(required = false) SpringWebfluxTelemetry springWebfluxTelemetry) {
        WebClientUtil.otelConfig = otelConfig;
        if (otelConfig.isTracingEnabled()) {
            WebClientUtil.springWebfluxTelemetry = springWebfluxTelemetry;
        }
    }

    public static WebClient buildWebClient(String baseURL, final HttpClient httpClient) {

        Object traceTag = new AtomicInteger().incrementAndGet();

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder() //
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) //
                .build();

        ExchangeFilterFunction reqLogger = ExchangeFilterFunction.ofRequestProcessor(req -> {
            logger.debug("{} {} uri = '{}''", traceTag, req.method(), req.url());
            return Mono.just(req);
        });

        ExchangeFilterFunction respLogger = ExchangeFilterFunction.ofResponseProcessor(resp -> {
            logger.debug("{} resp: {}", traceTag, resp.statusCode());
            return Mono.just(resp);
        });

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseURL)
                .exchangeStrategies(exchangeStrategies)
                .filter(reqLogger)
                .filter(respLogger);

        if (otelConfig.isSouthTracingEnabled()) {
            webClientBuilder.filters(springWebfluxTelemetry::addClientTracingFilter);
        }

        return webClientBuilder.build();
    }
}
