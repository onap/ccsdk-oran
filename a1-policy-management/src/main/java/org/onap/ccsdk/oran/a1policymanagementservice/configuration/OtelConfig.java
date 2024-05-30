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

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.extension.trace.jaeger.sampler.JaegerRemoteSampler;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;


@Configuration
@ComponentScan(basePackages = {"org.onap.ccsdk.oran.a1policymanagementservice"})
public class OtelConfig {

    public static final int JAEGER_REMOTE_SAMPLER_POLLING_INTERVAL_IN_SECOND = 30;
    private static final String SCHEDULED_TASK_NAME = "tasks.scheduled.execution";

    @Value("${spring.application.name}")
    private String serviceId;

    @Value("${tracing.exporter.endpoint}")
    private String tracingExporterEndpointUrl;

    @Value("${tracing.sampler.jaeger-remote.endpoint}")
    private String jaegerRemoteSamplerUrl;

    @Bean
    @ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnExpression("'grpc'.equals('${tracing.exporter.protocol}')")
    public OtlpGrpcSpanExporter otlpExporterGrpc() {
        return OtlpGrpcSpanExporter.builder().setEndpoint(tracingExporterEndpointUrl).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnExpression("'http'.equals('${tracing.exporter.protocol}')")
    public OtlpHttpSpanExporter otlpExporterHttp() {
        return OtlpHttpSpanExporter.builder().setEndpoint(tracingExporterEndpointUrl).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = false)
    public JaegerRemoteSampler jaegerRemoteSampler() {
        return JaegerRemoteSampler.builder().setEndpoint(jaegerRemoteSamplerUrl)
                .setPollingInterval(Duration.ofSeconds(JAEGER_REMOTE_SAMPLER_POLLING_INTERVAL_IN_SECOND))
                .setInitialSampler(Sampler.alwaysOff()).setServiceName(serviceId).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = false)
    ObservationRegistryCustomizer<ObservationRegistry> skipActuatorEndpointsFromObservation() {
        PathMatcher pathMatcher = new AntPathMatcher("/");
        return registry -> registry.observationConfig().observationPredicate(observationPredicate(pathMatcher));
    }

    static ObservationPredicate observationPredicate(PathMatcher pathMatcher) {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext observationContext) {
                return !pathMatcher.match("/actuator/**", observationContext.getCarrier().getRequestURI());
            } else {
                return !SCHEDULED_TASK_NAME.equals(name);
            }
        };
    }
}