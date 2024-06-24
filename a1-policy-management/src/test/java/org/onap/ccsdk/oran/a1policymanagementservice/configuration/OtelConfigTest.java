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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.AntPathMatcher;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
    "server.ssl.key-store=./config/keystore.jks",
    "app.webclient.trust-store=./config/truststore.jks",
    "app.webclient.trust-store-used=true",
    "app.vardata-directory=/tmp/pmstest",
    "app.filepath=",
    "app.s3.bucket=",
    "spring.application.name=a1-pms",
    "management.tracing.enabled=true",
    "management.tracing.exporter.protocol=grpc",
    "management.tracing.sampler.jaeger_remote.endpoint=http://127.0.0.1:14250",
    "management.tracing.propagator.type=W3C"
})
@AutoConfigureObservability
class OtelConfigTest {

    @Autowired private ApplicationContext context;

    @Autowired OtelConfig otelConfig;

    @Autowired ObservationRegistry observationRegistry;

    @Test
    void otlpExporterGrpc() {
        assertNotNull(otelConfig);
        assertNotNull(otelConfig.otlpExporterGrpc());
    }

    @Test
    void otlpExporterHttpNotActive() {
        assertNotNull(otelConfig);
        assertThrows(BeansException.class, () -> context.getBean(OtlpHttpSpanExporter.class));
    }

    @Test
    void jaegerRemoteSampler() {
        assertNotNull(otelConfig);
        assertNotNull(otelConfig.jaegerRemoteSampler());
    }

    @Test
    void skipActuatorEndpointsFromObservation() {
        assertNotNull(otelConfig);
        var actuatorCustomizer = otelConfig.skipActuatorEndpointsFromObservation();
        assertNotNull(actuatorCustomizer);
        Observation.Scope otelScope = Observation.Scope.NOOP;
        observationRegistry.setCurrentObservationScope(otelScope);
        Objects.requireNonNull(observationRegistry.getCurrentObservation()).start();
    }

    @Test
    void observationPredicate() {
        var antPathMatcher = new AntPathMatcher("/");
        ServerRequestObservationContext serverRequestObservationContext =
            mock(ServerRequestObservationContext.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/actuator/health");
        when(serverRequestObservationContext.getCarrier()).thenReturn(httpServletRequest);
        boolean result =
            OtelConfig.observationPredicate(antPathMatcher)
                .test("anything", serverRequestObservationContext);
        assertFalse(result);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/anything");
        result =
            OtelConfig.observationPredicate(antPathMatcher)
                .test("anything", serverRequestObservationContext);
        assertTrue(result);
    }
}
