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

import static ch.qos.logback.classic.Level.WARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.dmaap.DmaapRequestMessage.Operation;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Test
    void dmaapNotConfigured_thenSleepAndRetryUntilConfig() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        doNothing().when(messageConsumerUnderTest).sleep(any(Duration.class));
        doReturn(false, false, false, true).when(messageConsumerUnderTest).isStopped();
        doReturn(false, true, true).when(messageConsumerUnderTest).isDmaapConfigured();
        doReturn(new LinkedList<>()).when(messageConsumerUnderTest).fetchAllMessages();

        messageConsumerUnderTest.start().join();

        InOrder orderVerifier = inOrder(messageConsumerUnderTest);
        orderVerifier.verify(messageConsumerUnderTest).sleep(DmaapMessageConsumer.TIME_BETWEEN_DMAAP_RETRIES);
        orderVerifier.verify(messageConsumerUnderTest).fetchAllMessages();
    }

    @Test
    void dmaapConfigurationRemoved_thenStopPollingDmaapSleepAndRetry() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        doNothing().when(messageConsumerUnderTest).sleep(any(Duration.class));
        doReturn(false, false, false, false, true).when(messageConsumerUnderTest).isStopped();
        doReturn(true, true, false).when(messageConsumerUnderTest).isDmaapConfigured();
        doReturn(new LinkedList<>()).when(messageConsumerUnderTest).fetchAllMessages();

        messageConsumerUnderTest.start().join();

        InOrder orderVerifier = inOrder(messageConsumerUnderTest);
        orderVerifier.verify(messageConsumerUnderTest).fetchAllMessages();
        orderVerifier.verify(messageConsumerUnderTest).sleep(DmaapMessageConsumer.TIME_BETWEEN_DMAAP_RETRIES);
    }

    @Test
    void dmaapConfiguredAndNoMessages_thenPollOnce() throws Exception {
        setUpMrConfig();

        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        Mono<ResponseEntity<String>> response = Mono.empty();

        doReturn(false, true).when(messageConsumerUnderTest).isStopped();
        doReturn(messageRouterConsumerMock).when(messageConsumerUnderTest).getMessageRouterConsumer();
        doReturn(response).when(messageRouterConsumerMock).getForEntity(any());

        messageConsumerUnderTest.start().join();

        verify(messageRouterConsumerMock).getForEntity(any());
        verifyNoMoreInteractions(messageRouterConsumerMock);
    }

    @Test
    void dmaapConfiguredAndErrorGettingMessages_thenLogWarningAndSleep() throws Exception {
        setUpMrConfig();

        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        doNothing().when(messageConsumerUnderTest).sleep(any(Duration.class));
        doReturn(false, true).when(messageConsumerUnderTest).isStopped();
        doReturn(messageRouterConsumerMock).when(messageConsumerUnderTest).getMessageRouterConsumer();

        Mono<ResponseEntity<String>> response = Mono.just(new ResponseEntity<>("Error", HttpStatus.BAD_REQUEST));
        when(messageRouterConsumerMock.getForEntity(any())).thenReturn(response);

        final ListAppender<ILoggingEvent> logAppender =
                LoggingUtils.getLogListAppender(DmaapMessageConsumer.class, WARN);

        messageConsumerUnderTest.start().join();

        assertThat(logAppender.list.get(0).getFormattedMessage())
                .isEqualTo("Cannot fetch because of Error respons: 400 BAD_REQUEST Error");

        verify(messageConsumerUnderTest).sleep(DmaapMessageConsumer.TIME_BETWEEN_DMAAP_RETRIES);
    }

    @Test
    void dmaapConfiguredAndOneMessage_thenPollOnceAndProcessMessage() throws Exception {
        // The message from MR is here an array of Json objects
        setUpMrConfig();
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));

        String messages = jsonArray(gson.toJson(dmaapRequestMessage(Operation.PUT)));

        doReturn(false, true).when(messageConsumerUnderTest).isStopped();
        doReturn(messageRouterConsumerMock).when(messageConsumerUnderTest).getMessageRouterConsumer();

        Mono<ResponseEntity<String>> response = Mono.just(new ResponseEntity<>(messages, HttpStatus.OK));
        when(messageRouterConsumerMock.getForEntity(any())).thenReturn(response);

        doReturn(messageHandlerMock).when(messageConsumerUnderTest).getDmaapMessageHandler();

        messageConsumerUnderTest.start().join();

        ArgumentCaptor<DmaapRequestMessage> captor = ArgumentCaptor.forClass(DmaapRequestMessage.class);
        verify(messageHandlerMock).handleDmaapMsg(captor.capture());
        DmaapRequestMessage messageAfterJsonParsing = captor.getValue();
        assertThat(messageAfterJsonParsing.apiVersion()).isNotEmpty();

        verifyNoMoreInteractions(messageHandlerMock);
    }

    @Test
    void testMessageParsing() throws ServiceException {
        messageConsumerUnderTest = new DmaapMessageConsumer(applicationConfigMock);
        String json = gson.toJson(dmaapRequestMessage(Operation.PUT));
        {
            String jsonArrayOfObject = jsonArray(json);
            List<DmaapRequestMessage> parsedMessage = messageConsumerUnderTest.parseMessages(jsonArrayOfObject);
            assertNotNull(parsedMessage);
            assertTrue(parsedMessage.get(0).payload().isPresent());
        }
        {
            String jsonArrayOfString = jsonArray(quote(json));
            List<DmaapRequestMessage> parsedMessage = messageConsumerUnderTest.parseMessages(jsonArrayOfString);
            assertNotNull(parsedMessage);
            assertTrue(parsedMessage.get(0).payload().isPresent());
        }

    }

    @Test
    void incomingUnparsableRequest_thenSendResponse() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));
        doReturn(messageHandlerMock).when(messageConsumerUnderTest).getDmaapMessageHandler();
        doReturn(Mono.just("OK")).when(messageHandlerMock).sendDmaapResponse(any(), any(), any());
        Exception actualException =
                assertThrows(ServiceException.class, () -> messageConsumerUnderTest.parseMessages("[\"abc:\"def\"]"));
        assertThat(actualException.getMessage())
                .contains("Could not parse incomming request. Reason :com.google.gson.stream.MalformedJsonException");

        verify(messageHandlerMock).sendDmaapResponse(any(), any(), any());
    }

    @Test
    void incomingUnparsableRequest_thenSendingResponseFailed() throws Exception {
        messageConsumerUnderTest = spy(new DmaapMessageConsumer(applicationConfigMock));
        doReturn(messageHandlerMock).when(messageConsumerUnderTest).getDmaapMessageHandler();
        doReturn(Mono.error(new Exception("Sending response failed"))).when(messageHandlerMock).sendDmaapResponse(any(),
                any(), any());
        Exception actualException =
                assertThrows(Exception.class, () -> messageConsumerUnderTest.parseMessages("[\"abc:\"def\"]"));
        assertThat(actualException.getMessage()).contains("Sending response failed");

        verify(messageHandlerMock).sendDmaapResponse(any(), any(), any());
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

    private DmaapRequestMessage dmaapRequestMessage(Operation operation) {
        return ImmutableDmaapRequestMessage.builder() //
                .apiVersion("apiVersion") //
                .correlationId("correlationId") //
                .operation(operation) //
                .originatorId("originatorId") //
                .payload(new JsonObject()) //
                .requestId("requestId") //
                .target("target") //
                .timestamp("timestamp") //
                .url("URL") //
                .build();
    }

}
