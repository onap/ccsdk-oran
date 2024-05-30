/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024 Nordix Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@SpringBootTest(
    properties = {
        "tracing.enabled=true",
        "tracing.exporter.protocol=grpc",
        "exporter.endpoint=http://127.0.0.1:4317",
        "tracing.sampler.jaeger_remote.endpoint=http://127.0.0.1:14250"
    },
    webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureObservability
class OtelConfigTest {

    @Autowired private ApplicationContext context;

    @Autowired OtelConfig otelConfig;

    @Autowired ObservationRegistry observationRegistry;

    @Bean
    OpenTelemetry openTelemetry() {
        return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
    }

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
}
