/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2025 OpenInfra Foundation Europe. All rights reserved.
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
        // Check if the exclude paths are set in the application configuration
        String excludePaths = this.applicationConfig.getLoggingReactiveEntryExitFilterExcludePaths();
        if (excludePaths == null || excludePaths.isEmpty()) {
            return new ReactiveEntryExitFilter();
        }
        PathPatternParser parser = new PathPatternParser();
        String[] paths = excludePaths.split(",");
        ReactiveEntryExitFilter filter = new ReactiveEntryExitFilter();
        for (String path : paths) {
            filter.excludePathPatterns(parser.parse(path.trim()));
        }
        return filter;
    }
}
