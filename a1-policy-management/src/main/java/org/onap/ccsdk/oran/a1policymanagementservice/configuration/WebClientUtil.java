package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@DependsOn({"otelConfig"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebClientUtil {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static OtelConfig otelConfig;
    private static SpringWebfluxTelemetry webFluxTelemetry;

    public WebClientUtil(OtelConfig otelConfig, @Autowired(required = false) SpringWebfluxTelemetry webFluxTelemetry) {
        WebClientUtil.otelConfig = otelConfig;
        WebClientUtil.webFluxTelemetry = webFluxTelemetry;
    }

    public static WebClient buildWebClient(String baseURL, final HttpClient httpClient) {
        Object traceTag = new AtomicInteger().incrementAndGet();

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder() //
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) //
                .build();

        ExchangeFilterFunction reqLogger = ExchangeFilterFunction.ofRequestProcessor(req -> {
            logger.debug("{} {} uri = '{}''", traceTag, req.method(), req.url());
            return Mono.just(req);
        });

        ExchangeFilterFunction respLogger = ExchangeFilterFunction.ofResponseProcessor(resp -> {
            logger.debug("{} resp: {}", traceTag, resp.statusCode());
            return Mono.just(resp);
        });

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseURL)
                .exchangeStrategies(exchangeStrategies)
                .filter(reqLogger)
                .filter(respLogger);

        if (otelConfig.isTracingEnabled()) {
            webClientBuilder.filters(webFluxTelemetry::addClientTracingFilter);
        }

        return webClientBuilder.build();
    }
}
