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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import io.opentelemetry.sdk.extension.trace.jaeger.sampler.JaegerRemoteSampler;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.Getter;
import reactor.core.publisher.Hooks;

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

@Configuration
@ComponentScan(basePackages = {"org.onap.ccsdk.oran.a1policymanagementservice"})
public class OtelConfig {
    private static final Logger logger = LoggerFactory.getLogger(OtelConfig.class);

    public static final int JAEGER_REMOTE_SAMPLER_POLLING_INTERVAL_IN_SECOND = 30;

    @Value("${spring.application.name}")
    private String serviceId;

    @Value("${tracing.exporter.endpoint}")
    private String tracingExporterEndpointUrl;

    @Value("${tracing.sampler.jaeger-remote.endpoint}")
    private String jaegerRemoteSamplerUrl;

    @Value("${tracing.exporter.protocol}")
    private String tracingProtocol;

    @Getter
    @Value("${tracing.enabled}")
    private boolean tracingEnabled;

    @PostConstruct
    public void checkTracingConfig() {
        logger.info("Application Yaml Tracing Enabled: " + tracingEnabled);
    }

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
    public SpringWebfluxTelemetry webfluxTelemetry (OpenTelemetry openTelemetry) {
        //enables automatic context propagation to ThreadLocals used by FLUX and MONO operators
        Hooks.enableAutomaticContextPropagation();
        return SpringWebfluxTelemetry.builder(openTelemetry).build();
    }
}
