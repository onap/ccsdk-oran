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

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.StringVendorExtension;
import springfox.documentation.service.VendorExtension;
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
    static final String DESCRIPTION = //
            "The O-RAN Non-RT RIC Policy Management Service provides a REST API for management of A1 policices. \n" //
                    + "It provides support for:" //
                    + "<ul>" //
                    + "<li>A1 Policy creation and modification.</li>" //
                    + "<li>Maintaining a view of supported Near-RT RIC policy types </li>" //
                    + "<li>Supervision of using services (R-APPs). When a service is unavailble, its policies are removed. </li> " //
                    + "<li>Monitoring and maintaining consistency of the SMO view of A1 policies and the Near-RT RICs </li>" //
                    + "</ul>"//
    ;
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
                .extensions(vendorExtentions()) //
                .build();
    }

    @SuppressWarnings("java:S3740") // VendorExtension is a raw type. References to generic type VendorExtension<T>
                                    // should be parameterizedJava(16777788)
    private static List<VendorExtension> vendorExtentions() {
        final String URN = "60f9a0e7-343f-43bf-9194-d8d65688d465";
        List<VendorExtension> extentions = new ArrayList<>();
        extentions.add(new StringVendorExtension("x-api-id", URN));
        extentions.add(new StringVendorExtension("x-audience", "external-partner"));
        return extentions;
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
