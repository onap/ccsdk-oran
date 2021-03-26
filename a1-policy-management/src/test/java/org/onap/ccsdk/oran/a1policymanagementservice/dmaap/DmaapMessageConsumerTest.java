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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.dmaap.DmaapRequestMessage.Operation;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DmaapMessageConsumerTest {
    @Mock
    private ApplicationConfig applicationConfigMock;
    @Mock
    private AsyncRestClient messageRouterConsumerMock;
    @Mock
    private DmaapMessageHandler messageHandlerMock;

    private DmaapMessageConsumer messageConsumerUnderTest;

    private Gson gson = new GsonBuilder().create();

    @AfterEach
    void resetLogging() {
        LoggingUtils.getLogListAppender(DmaapMessageConsumer.class);
    }

    private void setTaskNumberOfLoops(int number) {
        ArrayList<Integer> l = new ArrayList<>();
        for (int i = 0; i < number; ++i) {
            l.add(i);
        }
        Flux<Integer> f = Flux.fromIterable(l);
        doReturn(f).when(messageConsumerUnderTest).infiniteFlux();
    }

    private void disableTaskDelay() {
        doReturn(Mono.empty()).when(messageConsumerUnderTest).delay();
    }

    @Test
    void successfulCase_dmaapNotConfigured_thenSleepAndRetryUntilConfig() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        setTaskNumberOfLoops(3);
        disableTaskDelay();

        when(this.applicationConfigMock.getDmaapConsumerTopicUrl()).thenReturn("getDmaapConsumerTopicUrl");
        doReturn(false, false, true).when(messageConsumerUnderTest).isDmaapConfigured();
        doReturn(Mono.just(dmaapRequestMessageString())).when(messageConsumerUnderTest)
                .getFromMessageRouter(anyString());

        doReturn(Mono.just("responseFromHandler")).when(messageConsumerUnderTest).handleDmaapMsg(any());

        String s = messageConsumerUnderTest.createTask().blockLast();
        assertEquals("responseFromHandler", s);
        verify(messageConsumerUnderTest, times(2)).delay();
        verify(messageConsumerUnderTest, times(1)).handleDmaapMsg(any());
    }

    @Test
    void returnErrorFromDmapp_thenSleepAndRetry() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        setTaskNumberOfLoops(2);
        disableTaskDelay();
        setUpMrConfig();

        {
            Mono<String> dmaapError = Mono.error(new ServiceException("dmaapError"));
            Mono<String> dmaapResponse = Mono.just(dmaapRequestMessageString());
            doReturn(dmaapError, dmaapResponse).when(messageConsumerUnderTest).getFromMessageRouter(anyString());
        }

        doReturn(Mono.just("response1")).when(messageConsumerUnderTest).handleDmaapMsg(any());

        String s = messageConsumerUnderTest.createTask().blockLast();

        verify(messageConsumerUnderTest, times(2)).getFromMessageRouter(anyString());
        verify(messageConsumerUnderTest, times(0)).sendErrorResponse(anyString());
        verify(messageConsumerUnderTest, times(1)).delay();
        verify(messageConsumerUnderTest, times(1)).handleDmaapMsg(any());
        assertEquals("response1", s);
    }

    @Test
    void unParsableMessage_thenSendResponseAndContinue() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));
        setTaskNumberOfLoops(2);
        setUpMrConfig();

        {
            Mono<String> dmaapError = Mono.just("Non valid JSON \"");
            Mono<String> dmaapResponse = Mono.just(dmaapRequestMessageString());
            doReturn(dmaapError, dmaapResponse).when(messageConsumerUnderTest).getFromMessageRouter(anyString());
        }

        doReturn(Mono.just("response1")).when(messageConsumerUnderTest).handleDmaapMsg(any());

        String s = messageConsumerUnderTest.createTask().blockLast();
        assertEquals("response1", s);

        verify(messageConsumerUnderTest, times(2)).getFromMessageRouter(anyString());
        verify(messageConsumerUnderTest, times(1)).sendErrorResponse(anyString());
        verify(messageConsumerUnderTest, times(0)).delay();
        verify(messageConsumerUnderTest, times(1)).handleDmaapMsg(dmaapRequestMessage());
    }

    private String dmaapRequestMessageString() {
        String json = gson.toJson(dmaapRequestMessage());
        return jsonArray(json);
    }

    @Test
    void testMessageParsing() throws ServiceException {
        messageConsumerUnderTest = new DmaapMessageConsumer(applicationConfigMock);
        String json = gson.toJson(dmaapRequestMessage());
        {
            String jsonArrayOfObject = jsonArray(json);
            DmaapRequestMessage parsedMessage =
                    messageConsumerUnderTest.parseReceivedMessage(jsonArrayOfObject).blockLast();
            assertNotNull(parsedMessage);
            assertNotNull(parsedMessage.getPayload());

            Assert.assertEquals(dmaapRequestMessage(), parsedMessage);
        }
        {
            String jsonArrayOfString = jsonArray(quote(json));
            DmaapRequestMessage parsedMessage =
                    messageConsumerUnderTest.parseReceivedMessage(jsonArrayOfString).blockLast();
            assertNotNull(parsedMessage);
            assertNotNull(parsedMessage.getPayload());
            Assert.assertEquals(dmaapRequestMessage(), parsedMessage);
        }

    }

    private void setUpMrConfig() {
        when(applicationConfigMock.getDmaapConsumerTopicUrl()).thenReturn("url");
        when(applicationConfigMock.getDmaapProducerTopicUrl()).thenReturn("url");
    }

    private String jsonArray(String s) {
        return "[" + s + "]";
    }

    private String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private DmaapRequestMessage dmaapRequestMessage() {
        return DmaapRequestMessage.builder() //
                .apiVersion("apiVersion") //
                .correlationId("correlationId") //
                .operation(Operation.PUT) //
                .originatorId("originatorId") //
                .payload(new JsonObject()) //
                .requestId("requestId") //
                .target("target") //
                .timestamp("timestamp") //
                .url("URL") //
                .build();
    }

}
