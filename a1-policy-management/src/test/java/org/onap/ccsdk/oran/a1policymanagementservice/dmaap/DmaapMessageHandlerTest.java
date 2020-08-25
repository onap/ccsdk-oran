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

package org.onap.ccsdk.oran.a1policymanagementservice.dmaap;

import static ch.qos.logback.classic.Level.WARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.dmaap.DmaapRequestMessage.Operation;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DmaapMessageHandlerTest {
    private static final Logger logger = LoggerFactory.getLogger(DmaapMessageHandlerTest.class);
    private static final String URL = "url";

    private final AsyncRestClient dmaapClient = mock(AsyncRestClient.class);
    private final AsyncRestClient pmsClient = mock(AsyncRestClient.class);
    private DmaapMessageHandler testedObject;
    private Gson gson = new GsonBuilder().create(); //

    @BeforeEach
    private void setUp() throws Exception {
        testedObject = spy(new DmaapMessageHandler(dmaapClient, pmsClient));
    }

    JsonObject payloadAsJson() {
        return gson.fromJson(payloadAsString(), JsonObject.class);
    }

    String payloadAsString() {
        return "{\"param\":\"value\"}";
    }

    DmaapRequestMessage dmaapRequestMessage(Operation operation) {
        Optional<JsonObject> payload =
            ((operation == Operation.PUT || operation == Operation.POST) ? Optional.of(payloadAsJson())
                : Optional.empty());
        return ImmutableDmaapRequestMessage.builder() //
            .apiVersion("apiVersion") //
            .correlationId("correlationId") //
            .operation(operation) //
            .originatorId("originatorId") //
            .payload(payload) //
            .requestId("requestId") //
            .target("target") //
            .timestamp("timestamp") //
            .url(URL) //
            .build();
    }

    private Mono<ResponseEntity<String>> okResponse() {
        ResponseEntity<String> entity = new ResponseEntity<>("OK", HttpStatus.OK);
        return Mono.just(entity);
    }

    private Mono<ResponseEntity<String>> notOkResponse() {
        ResponseEntity<String> entity = new ResponseEntity<>("NOK", HttpStatus.BAD_GATEWAY);
        return Mono.just(entity);
    }

    @Test
    void successfulDelete() throws IOException {
        doReturn(okResponse()).when(pmsClient).deleteForEntity(anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.DELETE);

        StepVerifier //
            .create(testedObject.createTask(message)) //
            .expectSubscription() //
            .expectNext("OK") //
            .verifyComplete(); //

        verify(pmsClient).deleteForEntity(URL);
        verifyNoMoreInteractions(pmsClient);

        verify(dmaapClient).post(anyString(), anyString());

        verifyNoMoreInteractions(dmaapClient);
    }

    @Test
    void successfulGet() throws IOException {
        doReturn(okResponse()).when(pmsClient).getForEntity(anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.GET);
        StepVerifier //
            .create(testedObject.createTask(message)) //
            .expectSubscription() //
            .expectNext("OK") //
            .verifyComplete(); //

        verify(pmsClient).getForEntity(URL);
        verifyNoMoreInteractions(pmsClient);

        verify(dmaapClient).post(anyString(), anyString());
        verifyNoMoreInteractions(dmaapClient);
    }

    @Test
    void exceptionFromPmsWhenGet_thenPostError() throws IOException {
        String errorBody = "Unavailable";
        WebClientResponseException webClientResponseException = new WebClientResponseException(
            HttpStatus.SERVICE_UNAVAILABLE.value(), "", (HttpHeaders) null, errorBody.getBytes(), (Charset) null);
        doReturn(Mono.error(webClientResponseException)).when(pmsClient).getForEntity(anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.GET);
        StepVerifier //
            .create(testedObject.createTask(message)) //
            .expectSubscription() //
            .verifyComplete(); //

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(dmaapClient).post(anyString(), captor.capture());
        String actualMessage = captor.getValue();
        assertThat(actualMessage).contains(HttpStatus.SERVICE_UNAVAILABLE.toString()) //
            .contains(errorBody);
    }

    @Test
    void successfulPut() throws IOException {
        doReturn(okResponse()).when(pmsClient).putForEntity(anyString(), anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.PUT);
        StepVerifier //
            .create(testedObject.createTask(message)) //
            .expectSubscription() //
            .expectNext("OK") //
            .verifyComplete(); //

        verify(pmsClient).putForEntity(URL, payloadAsString());
        verifyNoMoreInteractions(pmsClient);

        verify(dmaapClient).post(anyString(), anyString());
        verifyNoMoreInteractions(dmaapClient);
    }

    @Test
    void successfulPost() throws IOException {
        doReturn(okResponse()).when(pmsClient).postForEntity(anyString(), anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.POST);
        StepVerifier //
            .create(testedObject.createTask(message)) //
            .expectSubscription() //
            .expectNext("OK") //
            .verifyComplete(); //

        verify(pmsClient).postForEntity(URL, payloadAsString());
        verifyNoMoreInteractions(pmsClient);

        verify(dmaapClient).post(anyString(), anyString());
        verifyNoMoreInteractions(dmaapClient);
    }

    @Test
    void exceptionWhenCallingPms_thenErrorResponse() throws IOException {

        doReturn(notOkResponse()).when(pmsClient).putForEntity(anyString(), anyString());
        doReturn(Mono.just("OK")).when(dmaapClient).post(anyString(), anyString());

        DmaapRequestMessage message = dmaapRequestMessage(Operation.PUT);
        testedObject.createTask(message).block();

        verify(pmsClient).putForEntity(anyString(), anyString());
        verifyNoMoreInteractions(pmsClient);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(dmaapClient).post(anyString(), captor.capture());
        String actualMessage = captor.getValue();
        assertThat(actualMessage).as("Message \"%s\" sent to DMaaP contains %s", actualMessage, HttpStatus.BAD_GATEWAY)
            .contains(HttpStatus.BAD_GATEWAY.toString());

        verifyNoMoreInteractions(dmaapClient);
    }

    @Test
    void putWithoutPayload_thenNotFoundResponseWithWarning() throws Exception {
        DmaapRequestMessage message = ImmutableDmaapRequestMessage.builder() //
            .apiVersion("apiVersion") //
            .correlationId("correlationId") //
            .operation(DmaapRequestMessage.Operation.PUT) //
            .originatorId("originatorId") //
            .payload(Optional.empty()) //
            .requestId("requestId") //
            .target("target") //
            .timestamp("timestamp") //
            .url(URL) //
            .build();

        final ListAppender<ILoggingEvent> logAppender =
            LoggingUtils.getLogListAppender(DmaapMessageHandler.class, WARN);

        testedObject.handleDmaapMsg(message);

        assertThat(logAppender.list.get(0).getFormattedMessage())
            .startsWith("Expected payload in message from DMAAP: ");
    }

}
