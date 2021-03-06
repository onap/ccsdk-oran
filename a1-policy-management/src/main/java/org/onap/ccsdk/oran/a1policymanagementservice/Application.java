/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice;

import org.onap.ccsdk.oran.a1policymanagementservice.dmaap.DmaapMessageConsumer;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    @Autowired
    private RefreshConfigTask configRefresh;

    @Autowired
    private DmaapMessageConsumer dmaapMessageConsumer;

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    /**
     * Starts the configuration refresh task and reads the configuration.
     *
     * @param ctx the application context.
     *
     * @return the command line runner for the configuration refresh task.
     */
    @Bean
    public CommandLineRunner configRefreshRunner(ApplicationContext ctx) {
        return args -> configRefresh.start();
    }

    /**
     * Starts the DMaaP message consumer service.
     *
     * @param ctx the application context.
     *
     * @return the command line runner for the DMaaP message consumer service.
     */
    @Bean
    public CommandLineRunner dmaapMessageConsumerRunner(ApplicationContext ctx) {
        return args -> dmaapMessageConsumer.start();
    }
}
