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

import java.lang.invoke.MethodHandles;

import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    private final RefreshConfigTask configRefresh;
    Application(RefreshConfigTask configRefresh) {
        this.configRefresh = configRefresh;
    }

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                logger.warn("Shutting down, received signal SIGTERM");
                SpringApplication.exit(context);
            }
        });
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
}
