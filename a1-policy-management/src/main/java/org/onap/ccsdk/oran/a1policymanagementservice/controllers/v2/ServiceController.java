/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.api.v2.ServiceRegistryAndSupervisionApi;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceRegistrationInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceStatus;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceStatusList;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController("ServiceControllerV2")
@Tag( //
        name = ServiceController.API_NAME, //
        description = ServiceController.API_DESCRIPTION //

)
public class ServiceController implements ServiceRegistryAndSupervisionApi {

    public static final String API_NAME = "Service Registry and Supervision";
    public static final String API_DESCRIPTION = "";

    private final Services services;
    private final Policies policies;

    @Autowired
    private ObjectMapper objectMapper;

    private static Gson gson = new GsonBuilder().create();

    ServiceController(Services services, Policies policies) {
        this.services = services;
        this.policies = policies;
    }

    private static final String GET_SERVICE_DETAILS =
            "Either information about a registered service with given identity or all registered services are returned.";

    @Override
    public Mono<ResponseEntity<ServiceStatusList>> getServices(final String name, final ServerWebExchange exchange) throws Exception {
        if (name != null && this.services.get(name) == null) {
            throw new ServiceException("Service not found", HttpStatus.NOT_FOUND);
        }

        List<ServiceStatus> servicesStatus = new ArrayList<>();
        for (Service s : this.services.getAll()) {
            if (name == null || name.equals(s.getName())) {
                servicesStatus.add(toServiceStatus(s));
            }
        }
        return Mono.just(new ResponseEntity<>(new ServiceStatusList().serviceList(servicesStatus), HttpStatus.OK));
    }

    private ServiceStatus toServiceStatus(Service s) {
        return new ServiceStatus()
                .serviceId(s.getName())
                .keepAliveIntervalSeconds(s.getKeepAliveInterval().toSeconds())
                .timeSinceLastActivitySeconds(s.timeSinceLastPing().toSeconds())
                .callbackUrl(s.getCallbackUrl());
    }

    private void validateRegistrationInfo(ServiceRegistrationInfo registrationInfo)
            throws ServiceException, MalformedURLException {
        if (registrationInfo.getServiceId().isEmpty()) {
            throw new ServiceException("Missing mandatory parameter 'service-id'");
        }
        if (registrationInfo.getKeepAliveIntervalSeconds() < 0) {
            throw new ServiceException("Keep alive interval should be greater or equal to 0");
        }
        if (!registrationInfo.getCallbackUrl().isEmpty()) {
            new URL(registrationInfo.getCallbackUrl());
        }
    }

    private static final String REGISTER_SERVICE_DETAILS = "Registering a service is needed to:" //
            + "<ul>" //
            + "<li>Get callbacks about available NearRT RICs.</li>" //
            + "<li>Activate supervision of the service. If a service is inactive, its policies will automatically be deleted.</li>"//
            + "</ul>" //
            + "Policies can be created even if the service is not registerred. This is a feature which it is optional to use.";

    @Override
    public Mono<ResponseEntity<Object>> putService(
            final Mono<ServiceRegistrationInfo> registrationInfo, final ServerWebExchange exchange) {
            return registrationInfo.flatMap(info -> {
                try {
                    validateRegistrationInfo(info);
                } catch(Exception e) {
                    return ErrorResponse.createMono(e, HttpStatus.BAD_REQUEST);
                }
                final boolean isCreate = this.services.get(info.getServiceId()) == null;
                this.services.put(toService(info));
                return Mono.just(new ResponseEntity<>(isCreate ? HttpStatus.CREATED : HttpStatus.OK));
            }).onErrorResume(Exception.class, e -> ErrorResponse.createMono(e, HttpStatus.BAD_REQUEST));
    }

    @Override
    public Mono<ResponseEntity<Object>> deleteService(final String serviceId, final ServerWebExchange exchange) {
        try {
            Service service = removeService(serviceId);
            // Remove the policies from the repo and let the consistency monitoring
            // do the rest.
            removePolicies(service);
            return Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT));
        } catch (ServiceException e) {
            return ErrorResponse.createMono(e, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public Mono<ResponseEntity<Object>> keepAliveService(final String serviceId, final ServerWebExchange exchange) throws ServiceException {

            services.getService(serviceId).keepAlive();
            return Mono.just(new ResponseEntity<>(HttpStatus.OK));
    }

    private Service removeService(String name) throws ServiceException {
        Service service = this.services.getService(name); // Just to verify that it exists
        this.services.remove(service.getName());
        return service;
    }

    private void removePolicies(Service service) {
        Collection<Policy> policyList = this.policies.getForService(service.getName());
        for (Policy policy : policyList) {
            this.policies.remove(policy);
        }
    }

    private Service toService(ServiceRegistrationInfo s) {
        return new Service(s.getServiceId(), Duration.ofSeconds(s.getKeepAliveIntervalSeconds()), s.getCallbackUrl());
    }

}
