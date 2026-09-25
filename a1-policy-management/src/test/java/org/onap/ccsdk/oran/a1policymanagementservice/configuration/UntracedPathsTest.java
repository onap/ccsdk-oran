/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2026 Deutsche Telekom AG. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.UrlAttributes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
    "server.ssl.enabled=false",
    "app.vardata-directory=/tmp/pmstest",
    "app.filepath=",
    "app.s3.bucket=",
    "spring.application.name=a1-pms",
    "otel.sdk.disabled=false",
    "otel.traces.exporter=collecting",
    "otel.bsp.schedule.delay=10",
    "otel.tracing.sampler.jaeger-remote.endpoint=http://127.0.0.1:14250"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UntracedPathsTest {

    private static final AttributeKey<String> MICROMETER_URI = AttributeKey.stringKey("uri");

    private static final List<SpanData> exported = new CopyOnWriteArrayList<>();

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @TestConfiguration
    static class CollectingExporterConfig {
        @Bean
        ConfigurableSpanExporterProvider collectingSpanExporterProvider() {
            return new ConfigurableSpanExporterProvider() {
                @Override
                public SpanExporter createExporter(ConfigProperties config) {
                    return new SpanExporter() {
                        @Override
                        public CompletableResultCode export(Collection<SpanData> spans) {
                            exported.addAll(spans);
                            return CompletableResultCode.ofSuccess();
                        }

                        @Override
                        public CompletableResultCode flush() {
                            return CompletableResultCode.ofSuccess();
                        }

                        @Override
                        public CompletableResultCode shutdown() {
                            return CompletableResultCode.ofSuccess();
                        }
                    };
                }

                @Override
                public String getName() {
                    return "collecting";
                }
            };
        }
    }

    @Test
    void healthCheckRequestsAreNotExported() throws Exception {
        List<String> untraced = List.of("/status", "/a1-policy/v2/status", "/a1-policy-management/v1/status",
                "/actuator/health", "/actuator/health/liveness");
        for (String path : untraced) {
            get(path);
        }
        String traced = "/a1-policy-management/v1/rics";
        get(traced);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> exportedServerPaths().stream().filter(traced::equals).count() == 2);
        assertThat(exportedServerPaths()).doesNotContainAnyElementsOf(untraced);
    }

    private void get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static List<String> exportedServerPaths() {
        return exported.stream().filter(span -> span.getKind() == SpanKind.SERVER)
                .map(UntracedPathsTest::serverPath).toList();
    }

    // Each request produces one server span from the OpenTelemetry starter and one from Micrometer,
    // and the two record the path under different attributes.
    private static String serverPath(SpanData span) {
        String path = span.getAttributes().get(UrlAttributes.URL_PATH);
        return path != null ? path : span.getAttributes().get(MICROMETER_URI);
    }
}
