/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2020 Nordix Foundation. All rights reserved.
 * Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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

import org.apache.catalina.connector.Connector;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.tomcat.reactive.TomcatReactiveWebServerFactory;
import org.springframework.boot.web.server.reactive.ReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Configuration
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
public class BeanFactory {

    @Value("${server.http-port:0}")
    private int httpPort = 0;

    @Bean 
    public ObjectMapper objectMapper() {
    	return new ObjectMapper();
    }

    @Bean
    public Gson gson() {
      GsonBuilder gsonBuilder = new GsonBuilder();
      return gsonBuilder.create();
    }

    @Bean
    public ApplicationConfig getApplicationConfig() {
        return new ApplicationConfig();
    }

    @Bean
    public Rics getRics() {
        return new Rics();
    }

    @Bean
    public A1ClientFactory getA1ClientFactory(@Autowired ApplicationConfig applicationConfig,
            @Autowired SecurityContext securityContext) {
        return new A1ClientFactory(applicationConfig, securityContext);
    }

  @Bean
  public ReactiveWebServerFactory servletContainer() {
      TomcatReactiveWebServerFactory tomcat = new TomcatReactiveWebServerFactory();
      if (httpPort > 0) {
          tomcat.addAdditionalConnectors(getHttpConnector(httpPort));
      }
      return tomcat;
  }

  private static Connector getHttpConnector(int httpPort) {
      Connector connector = new Connector(TomcatReactiveWebServerFactory.DEFAULT_PROTOCOL);
      connector.setScheme("http");
      connector.setPort(httpPort);
      connector.setSecure(false);
      return connector;
  }

}
