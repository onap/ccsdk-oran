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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = Consts.V1_API_NAME)
public class ServiceController {

    private final Services services;
    private final Policies policies;

    private static Gson gson = new GsonBuilder().create();

    @Autowired
    ServiceController(Services services, Policies policies) {
        this.services = services;
        this.policies = policies;
    }

    @GetMapping("/services")
    @Operation(summary = "Returns service information")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "OK", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ServiceStatus.class)))), //
            @ApiResponse(responseCode = "404", //
                    description = "Service is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })

    public ResponseEntity<String> getServices(//
            @Parameter(name = "name", required = false, description = "The name of the service") //
            @RequestParam(name = "name", required = false) String name) {
        if (name != null && this.services.get(name) == null) {
            return new ResponseEntity<>("Service not found", HttpStatus.NOT_FOUND);
        }

        Collection<ServiceStatus> servicesStatus = new ArrayList<>();
        for (var service : this.services.getAll()) {
            if (name == null || name.equals(service.getName())) {
                servicesStatus.add(toServiceStatus(service));
            }
        }

        var res = gson.toJson(servicesStatus);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    private ServiceStatus toServiceStatus(Service s) {
        return new ServiceStatus(s.getName(), s.getKeepAliveInterval().toSeconds(), s.timeSinceLastPing().toSeconds(),
                s.getCallbackUrl());
    }

    private void validateRegistrationInfo(ServiceRegistrationInfo registrationInfo)
            throws ServiceException, MalformedURLException {
        if (registrationInfo.serviceName.isEmpty()) {
            throw new ServiceException("Missing mandatory parameter 'serviceName'");
        }
        if (registrationInfo.keepAliveIntervalSeconds < 0) {
            throw new ServiceException("Keepalive interval should be greater or equal to 0");
        }
        if (!registrationInfo.callbackUrl.isEmpty()) {
            new URL(registrationInfo.callbackUrl);
        }
    }

    @Operation(summary = "Register a service")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Service updated", //
                    content = @Content(schema = @Schema(implementation = String.class))), //
            @ApiResponse(responseCode = "201", //
                    description = "Service created", //
                    content = @Content(schema = @Schema(implementation = String.class))), //
            @ApiResponse(responseCode = "400", //
                    description = "The ServiceRegistrationInfo is not accepted", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })
    @PutMapping("/service")
    public ResponseEntity<String> putService(//
            @RequestBody ServiceRegistrationInfo registrationInfo) {
        try {
            validateRegistrationInfo(registrationInfo);
            final var isCreate = this.services.get(registrationInfo.serviceName) == null;
            this.services.put(toService(registrationInfo));
            return new ResponseEntity<>("OK", isCreate ? HttpStatus.CREATED : HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Unregister a service")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "204", description = "Service unregistered", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found", //
                    content = @Content(schema = @Schema(implementation = String.class)))})
    @DeleteMapping("/services")
    public ResponseEntity<String> deleteService(//
            @Parameter(name = "name", required = true, description = "The name of the service") //
            @RequestParam(name = "name", required = true) String serviceName) {
        try {
            var service = removeService(serviceName);
            // Remove the policies from the repo and let the consistency monitoring
            // do the rest.
            removePolicies(service);
            return new ResponseEntity<>("OK", HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Heartbeat from a service")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", description = "Service supervision timer refreshed, OK"),
            @ApiResponse(responseCode = "404", description = "The service is not found, needs re-registration")

    })

    @PutMapping("/services/keepalive")
    public ResponseEntity<String> keepAliveService(//
            @Parameter(name = "name", required = true, description = "The name of the service") //
            @RequestParam(name = "name", required = true) String serviceName) {
        try {
            services.getService(serviceName).keepAlive();
            return new ResponseEntity<>("OK", HttpStatus.OK);
        } catch (ServiceException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    private Service removeService(String name) throws ServiceException {
        var service = this.services.getService(name); // Just to verify that it exists
        this.services.remove(service.getName());
        return service;
    }

    private void removePolicies(Service service) {
        Collection<Policy> policyList = this.policies.getForService(service.getName());
        for (var policy : policyList) {
            this.policies.remove(policy);
        }
    }

    private Service toService(ServiceRegistrationInfo s) {
        return new Service(s.serviceName, Duration.ofSeconds(s.keepAliveIntervalSeconds), s.callbackUrl);
    }

}
