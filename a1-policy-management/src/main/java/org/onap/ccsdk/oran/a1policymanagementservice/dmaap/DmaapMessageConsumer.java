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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * The class fetches incoming requests from DMAAP. It uses the timeout parameter
 * that lets the MessageRouter keep the connection with the Kafka open until
 * requests are sent in.
 *
 * <p>
 * this service will regularly check the configuration and start polling DMaaP
 * if the configuration is added. If the DMaaP configuration is removed, then
 * the service will stop polling and resume checking for configuration.
 *
 * <p>
 * Each received request is processed by {@link DmaapMessageHandler}.
 */
@Component
public class DmaapMessageConsumer {

    protected static final Duration TIME_BETWEEN_DMAAP_RETRIES = Duration.ofSeconds(10);

    private static final Logger logger = LoggerFactory.getLogger(DmaapMessageConsumer.class);

    private final ApplicationConfig applicationConfig;

    private DmaapMessageHandler dmaapMessageHandler = null;

    private final Gson gson;

    private final AsyncRestClientFactory restClientFactory;

    private final InfiniteFlux infiniteSubmitter = new InfiniteFlux();

    @Value("${server.http-port}")
    private int localServerHttpPort;

    private static class InfiniteFlux {
        private FluxSink<Integer> sink;
        private int counter = 0;

        public synchronized Flux<Integer> start() {
            stop();
            return Flux.create(this::next).doOnRequest(this::onRequest);
        }

        public synchronized void stop() {
            if (this.sink != null) {
                this.sink.complete();
                this.sink = null;
            }
        }

        void onRequest(long no) {
            logger.debug("InfiniteFlux.onRequest {}", no);
            for (long i = 0; i < no; ++i) {
                sink.next(counter++);
            }
        }

        void next(FluxSink<Integer> sink) {
            logger.debug("InfiniteFlux.next");
            this.sink = sink;
            sink.next(counter++);
        }

    }

    @Autowired
    public DmaapMessageConsumer(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        GsonBuilder gsonBuilder = new GsonBuilder();
        this.gson = gsonBuilder.create();
        this.restClientFactory = new AsyncRestClientFactory(applicationConfig.getWebClientConfig());
    }

    /**
     * Starts the DMAAP consumer. If there is a DMaaP configuration, it will start
     * polling for messages. Otherwise it will check regularly for the
     * configuration.
     *
     */
    public void start() {
        infiniteSubmitter.stop();

        createTask().subscribe(//
                value -> logger.debug("DmaapMessageConsumer next: {}", value), //
                throwable -> logger.error("DmaapMessageConsumer error: {}", throwable.getMessage()), //
                () -> logger.warn("DmaapMessageConsumer stopped") //
        );
    }

    protected Flux<String> createTask() {
        return infiniteFlux() //
                .flatMap(notUsed -> fetchFromDmaap(), 1) //
                .doOnNext(message -> logger.debug("Message Reveived from DMAAP : {}", message)) //
                .flatMap(this::parseReceivedMessage, 1)//
                .flatMap(this::handleDmaapMsg, 1) //
                .onErrorResume(throwable -> Mono.empty());
    }

    protected Flux<Integer> infiniteFlux() {
        return infiniteSubmitter.start();
    }

    protected Mono<Object> delay() {
        return Mono.delay(TIME_BETWEEN_DMAAP_RETRIES).flatMap(o -> Mono.empty());
    }

    private <T> List<T> parseList(String jsonString, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        JsonArray jsonArr = JsonParser.parseString(jsonString).getAsJsonArray();
        for (JsonElement jsonElement : jsonArr) {
            // The element can either be a JsonObject or a JsonString
            if (jsonElement.isJsonPrimitive()) {
                T json = gson.fromJson(jsonElement.getAsString(), clazz);
                result.add(json);
            } else {
                T json = gson.fromJson(jsonElement.toString(), clazz);
                result.add(json);
            }
        }
        return result;
    }

    protected boolean isDmaapConfigured() {
        String producerTopicUrl = applicationConfig.getDmaapProducerTopicUrl();
        String consumerTopicUrl = applicationConfig.getDmaapConsumerTopicUrl();
        return (producerTopicUrl != null && consumerTopicUrl != null && !producerTopicUrl.isEmpty()
                && !consumerTopicUrl.isEmpty());
    }

    protected Mono<String> handleDmaapMsg(DmaapRequestMessage dmaapRequestMessage) {
        return getDmaapMessageHandler().handleDmaapMsg(dmaapRequestMessage);
    }

    protected Mono<String> getFromMessageRouter(String topicUrl) {
        logger.trace("getFromMessageRouter {}", topicUrl);
        AsyncRestClient c = restClientFactory.createRestClientNoHttpProxy("");
        return c.get(topicUrl);
    }

    protected Flux<DmaapRequestMessage> parseReceivedMessage(String jsonString) {
        try {
            logger.trace("parseMessages {}", jsonString);
            return Flux.fromIterable(parseList(jsonString, DmaapRequestMessage.class));
        } catch (Exception e) {
            logger.error("parseMessages error {}", jsonString);
            return sendErrorResponse("Could not parse: " + jsonString) //
                    .flatMapMany(s -> Flux.empty());
        }
    }

    protected Mono<String> sendErrorResponse(String response) {
        logger.debug("sendErrorResponse {}", response);
        DmaapRequestMessage fakeRequest = DmaapRequestMessage.builder() //
                .apiVersion("") //
                .correlationId("") //
                .operation(DmaapRequestMessage.Operation.PUT) //
                .originatorId("") //
                .payload(null) //
                .requestId("") //
                .target("") //
                .timestamp("") //
                .url("URL") //
                .build();
        return getDmaapMessageHandler().sendDmaapResponse(response, fakeRequest, HttpStatus.BAD_REQUEST) //
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<String> fetchFromDmaap() {
        if (!this.isDmaapConfigured()) {
            logger.debug("fetchFromDmaap, no action DMAAP not configured");
            return delay().flatMap(o -> Mono.empty());
        }
        logger.debug("fetchFromDmaap");
        String topicUrl = this.applicationConfig.getDmaapConsumerTopicUrl();

        return getFromMessageRouter(topicUrl) //
                .onErrorResume(throwable -> delay().flatMap(o -> Mono.empty()));
    }

    private DmaapMessageHandler getDmaapMessageHandler() {
        if (this.dmaapMessageHandler == null) {
            String pmsBaseUrl = "http://localhost:" + this.localServerHttpPort;
            AsyncRestClient pmsClient = restClientFactory.createRestClientNoHttpProxy(pmsBaseUrl);
            AsyncRestClient producer =
                    restClientFactory.createRestClientNoHttpProxy(this.applicationConfig.getDmaapProducerTopicUrl());
            this.dmaapMessageHandler = new DmaapMessageHandler(producer, pmsClient);
        }
        return this.dmaapMessageHandler;
    }

}
