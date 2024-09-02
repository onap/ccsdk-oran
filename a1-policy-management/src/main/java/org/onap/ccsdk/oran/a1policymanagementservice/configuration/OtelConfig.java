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

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import io.opentelemetry.sdk.extension.trace.jaeger.sampler.JaegerRemoteSampler;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.time.Duration;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

@Configuration
@ComponentScan(basePackages = {"org.onap.ccsdk.oran.a1policymanagementservice"})
public class OtelConfig {
    private static final Logger logger = LoggerFactory.getLogger(OtelConfig.class);

    public static final int JAEGER_REMOTE_SAMPLER_POLLING_INTERVAL_IN_SECOND = 30;

    @Value("${spring.application.name}")
    private String serviceId;

    @Value("${otel.exporter.otlp.traces.endpoint}")
    private String tracingExporterEndpointUrl;

    @Value("${otel.tracing.sampler.jaeger-remote.endpoint}")
    private String jaegerRemoteSamplerUrl;

    @Value("${otel.exporter.otlp.traces.protocol}")
    private String tracingProtocol;

    @Value("${otel.sdk.disabled}")
    private boolean tracingDisabled;

    @Value("${otel.sdk.south}")
    private boolean southTracingEnabled;

    @PostConstruct
    public void checkTracingConfig() {
        logger.info("Application Yaml Tracing Enabled: {}", !tracingDisabled);
    }

    public boolean isTracingEnabled() {
        return !tracingDisabled;
    }

    public boolean isSouthTracingEnabled() {
        return isTracingEnabled() && southTracingEnabled;
    }

    @Bean
    @ConditionalOnProperty(prefix = "otel.sdk", name = "disabled", havingValue = "false", matchIfMissing = false)
    @ConditionalOnExpression("'grpc'.equals('${otel.exporter.otlp.traces.protocol}')")
    public OtlpGrpcSpanExporter otlpExporterGrpc() {
        return OtlpGrpcSpanExporter.builder().setEndpoint(tracingExporterEndpointUrl).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "otel.sdk", name = "disabled", havingValue = "false", matchIfMissing = false)
    @ConditionalOnExpression("'http'.equals('${otel.exporter.otlp.traces.protocol}')")
    public OtlpHttpSpanExporter otlpExporterHttp() {
        return OtlpHttpSpanExporter.builder().setEndpoint(tracingExporterEndpointUrl).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "otel.sdk", name = "disabled", havingValue = "false", matchIfMissing = false)
    public JaegerRemoteSampler jaegerRemoteSampler() {
        return JaegerRemoteSampler.builder().setEndpoint(jaegerRemoteSamplerUrl)
                .setPollingInterval(Duration.ofSeconds(JAEGER_REMOTE_SAMPLER_POLLING_INTERVAL_IN_SECOND))
                .setInitialSampler(Sampler.alwaysOff()).setServiceName(serviceId).build();
    }

    @Bean
    @ConditionalOnExpression("!${otel.sdk.disabled:true} and ${otel.sdk.south:true}")
    public SpringWebfluxTelemetry webfluxTelemetry (OpenTelemetry openTelemetry) {
        return SpringWebfluxTelemetry.builder(openTelemetry).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "otel.sdk", name = "disabled", havingValue = "false", matchIfMissing = false)
    ObservationRegistryCustomizer<ObservationRegistry> skipActuatorEndpointsFromObservation() {
        PathMatcher pathMatcher = new AntPathMatcher("/");
        return registry ->
            registry.observationConfig().observationPredicate(observationPredicate(pathMatcher));
    }

    static ObservationPredicate observationPredicate(PathMatcher pathMatcher) {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext observationContext) {
                return !pathMatcher.match("/actuator/**", observationContext.getCarrier().getRequestURI());
            } else {
                return false;
            }
        };
    }
}
