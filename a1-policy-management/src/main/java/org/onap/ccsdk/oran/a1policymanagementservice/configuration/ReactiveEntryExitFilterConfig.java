package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.ReactiveEntryExitFilter;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.ReactiveEntryExitFilterCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class ReactiveEntryExitFilterConfig {
    @Bean
    @Conditional(ReactiveEntryExitFilterCondition.class)
    public WebFilter reactiveEntryExitFilter() {
        return new ReactiveEntryExitFilter();
    }
}
