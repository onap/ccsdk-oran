/*-
 * ========================LICENSE_START=================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v3;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v3.ConfigurationApi;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ConfigurationController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.Consts;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("ConfigurationControllerV3")
@Tag( //
        name = ConfigurationControllerV3.API_NAME, //
        description = ConfigurationControllerV3.API_DESCRIPTION //
)
@RequestMapping(Consts.V3_API_ROOT)
public class ConfigurationControllerV3 implements ConfigurationApi {

    public static final String API_NAME = "Management of configuration";
    public static final String API_DESCRIPTION = "API used to create or fetch the application configuration";
    private final ConfigurationController configurationController;

    public ConfigurationControllerV3(ConfigurationController configurationController) {
        this.configurationController = configurationController;
    }

    @Override
    public Mono<ResponseEntity<String>> getConfiguration(ServerWebExchange exchange) throws Exception {
        return configurationController.getConfiguration(exchange);
    }

    @Override
    public Mono<ResponseEntity<Object>> putConfiguration(Mono<Object> body, ServerWebExchange exchange) throws Exception {
        return configurationController.putConfiguration(body, exchange);
    }
}
