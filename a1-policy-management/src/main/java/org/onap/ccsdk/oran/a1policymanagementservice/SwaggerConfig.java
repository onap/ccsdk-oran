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

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ConfigurationController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.PolicyController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.RicRepositoryController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ServiceController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.StatusController;

/**
 * Swagger configuration class that uses swagger2 documentation type and scans
 * all the controllers under
 * org.onap.ccsdk.oran.a1policymanagementservice.controllers package. To access
 * the swagger gui go to http://ip:port/swagger-ui.html
 *
 */
@OpenAPIDefinition( //
        tags = { //
                @Tag(//
                        name = org.onap.ccsdk.oran.a1policymanagementservice.controllers.v1.Consts.V1_API_NAME, //
                        description = org.onap.ccsdk.oran.a1policymanagementservice.controllers.v1.Consts.V1_API_DESCRIPTION //
                ), //
                @Tag(//
                        name = ConfigurationController.API_NAME, //
                        description = ConfigurationController.API_DESCRIPTION //
                ), //
                @Tag(//
                        name = PolicyController.API_NAME, //
                        description = PolicyController.API_DESCRIPTION //
                ), //
                @Tag( //
                        name = RicRepositoryController.API_NAME, //
                        description = RicRepositoryController.API_DESCRIPTION //
                ), //
                @Tag( //
                        name = ServiceController.API_NAME, //
                        description = ServiceController.API_DESCRIPTION //

                ), //
                @Tag( //
                        name = StatusController.API_NAME, //
                        description = StatusController.API_DESCRIPTION //
                ), //
                @Tag( //
                        name = Consts.V2_API_SERVICE_CALLBACKS_NAME, //
                        description = Consts.V2_API_SERVICE_CALLBACKS_DESCRIPTION //
                ) //
        }, //
        info = @Info(title = SwaggerConfig.API_TITLE, //
                version = SwaggerConfig.VERSION, //
                description = SwaggerConfig.DESCRIPTION, //
                license = @License(name = "Copyright (C) 2020 Nordix Foundation. Licensed under the Apache License.", //
                        url = "http://www.apache.org/licenses/LICENSE-2.0")) //
)
public class SwaggerConfig {
    private SwaggerConfig() {}

    private static final String H3 = "<h3>";
    private static final String H3_END = "</h3>";

    public static final String API_TITLE = "A1 Policy management service";
    static final String DESCRIPTION = "<h2>General</h2>" + //
            "<p>The O-RAN Non-RT RIC Policy Management Service provides a REST API for management of A1 policices. <br/>The main tasks of the service are:</p>"
            + //
            "<ul>" + //
            "<li>A1 Policy creation, modification and deletion.</li>" + //
            "<li>Monitoring and maintaining consistency of the SMO view of A1 policies and the Near-RT RICs</li>" + //
            "<li>Maintaining a view of supported Near-RT RIC policy types</li>" + //
            "<li>Supervision of using services (R-APPs). When a service is unavailble, its policies are removed.</li>" + //
            "</ul>" + //
            "<h2>APIs provided by the service</h2>" + //
            H3 + PolicyController.API_NAME + H3_END + //
            "<p>This is an API for management of A1 Policies.</p>" + //
            "<ul>" + //
            "<li>A1 Policy retrieval, creation, modification and deletion.</li>" + //
            "<li>Retrieval of supported A1 Policy types for a Near-RT RIC</li>" + //
            "<li>Retrieval of status for existing A1 policies</li>" + //
            "</ul>" + //

            H3 + ConfigurationController.API_NAME + H3_END + //
            "<p>API for updating and retrieval of the component configuration. Note that there other ways to maintain the configuration.</p>"
            + //
            H3 + Consts.V2_API_SERVICE_CALLBACKS_NAME + H3_END + //
            "<p>These are endpoints that are invoked by this service. The callbacks are registerred in this service at service registration.</p>"
            + //
            H3 + RicRepositoryController.API_NAME + H3_END + //
            "<p>This is an API that provides support for looking up a NearRT-RIC. Each A1 policy is targeted for one Near-RT RIC.</p>"
            + H3 + StatusController.API_NAME + H3_END + //
            "<p>API used for supervision of the PMS component.</p>" + //
            H3 + ServiceController.API_NAME + H3_END + //
            "<p>API used for registerring services that uses PMS. Each A1 policy is owned by a service. PMS can supervise each registerred service and will automatically remove policies for unavailable services.</p>";

    public static final String VERSION = "1.1.0";
}
