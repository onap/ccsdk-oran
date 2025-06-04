package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.ReactiveEntryExitFilter;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.ReactiveEntryExitFilterCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class ReactiveEntryExitFilterConfig {

    private ApplicationConfig applicationConfig;

    public ReactiveEntryExitFilterConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    @Bean
    @Conditional(ReactiveEntryExitFilterCondition.class)
    public WebFilter reactiveEntryExitFilter() {
        // Check if the exclude path is set in the application configuration
        String excludePath = this.applicationConfig.getLoggingReactiveEntryExitFilterExcludePath();
        if (excludePath == null || excludePath.isEmpty()) {
            return new ReactiveEntryExitFilter();
        }
        PathPatternParser parser = new PathPatternParser();
        return new ReactiveEntryExitFilter()
                .excludePathPatterns(parser.parse(excludePath));
    }
}
