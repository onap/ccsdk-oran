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

import com.google.common.base.Predicates;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Swagger configuration class that uses swagger2 documentation type and scans
 * all the controllers under
 * org.onap.ccsdk.oran.a1policymanagementservice.controllers package. To access
 * the swagger gui go to http://ip:port/swagger-ui.html
 *
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig extends WebMvcConfigurationSupport {

    static final String API_TITLE = "A1 Policy management service";
    static final String DESCRIPTION =
        "The O-RAN Non-RT RIC PolicyAgent provides a REST API for management of policices. \n"
            + "It provides support for: \n"
            + "-Supervision of clients (R-APPs) to eliminate stray policies in case of failure \n"
            + "-Consistency monitoring of the SMO view of policies and the actual situation in the RICs \n"
            + "-Consistency monitoring of RIC capabilities (policy types)" + "-Policy configuration. \n"
            + "This includes:" + "-One REST API towards all RICs in the network \n"
            + "-Query functions that can find all policies in a RIC, all policies owned by a service (R-APP), \n"
            + "all policies of a type etc. \n"
            + "-Maps O1 resources (ManagedElement) as defined in O1 to the controlling RIC of A1 policices.\n";
    static final String VERSION = "1.1.0";
    @SuppressWarnings("squid:S1075") // Refactor your code to get this URI from a customizable parameter.
    static final String RESOURCES_PATH = "classpath:/META-INF/resources/";
    static final String WEBJARS_PATH = RESOURCES_PATH + "webjars/";
    static final String SWAGGER_UI = "swagger-ui.html";
    static final String WEBJARS = "/webjars/**";

    /**
     * Gets the API info.
     *
     * @return the API info.This page lists all the rest apis for the service.
     */
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2) //
            .apiInfo(apiInfo()) //
            .select() //
            .apis(RequestHandlerSelectors.any()) //
            .paths(PathSelectors.any()) //
            .paths(Predicates.not(PathSelectors.regex("/error"))) //
            // this endpoint is not implemented, but was visible for Swagger
            .paths(Predicates.not(PathSelectors.regex("/actuator.*"))) //
            // this endpoint is implemented by spring framework, exclude for now
            .build();
    }

    private static ApiInfo apiInfo() {
        return new ApiInfoBuilder() //
            .title(API_TITLE) //
            .description(DESCRIPTION) //
            .version(VERSION) //
            .contact(contact()) //
            .build();
    }

    private static Contact contact() {
        return new Contact("Ericsson Software Technology", "", "nonrtric@est.tech");
    }

    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(SWAGGER_UI) //
            .addResourceLocations(RESOURCES_PATH);

        registry.addResourceHandler(WEBJARS) //
            .addResourceLocations(WEBJARS_PATH);
    }

}
