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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("ServiceControllerV2")
@Api(tags = Consts.V2_API_NAME)
public class ServiceController {

    private final Services services;
    private final Policies policies;

    private static Gson gson = new GsonBuilder() //
            .create(); //

    @Autowired
    ServiceController(Services services, Policies policies) {
        this.services = services;
        this.policies = policies;
    }

    private static final String GET_SERVICE_DETAILS =
            "Either information about a registered service with given identity or all registered services are returned.";

    @GetMapping(path = Consts.V2_API_ROOT + "/services", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Returns service information", notes = GET_SERVICE_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "OK", response = ServiceStatusList.class), //
            @ApiResponse(code = 404, message = "Service is not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getServices(//
            @ApiParam(name = Consts.SERVICE_ID_PARAM, required = false, value = "The identity of the service") //
            @RequestParam(name = Consts.SERVICE_ID_PARAM, required = false) String name) {
        if (name != null && this.services.get(name) == null) {
            return ErrorResponse.create("Service not found", HttpStatus.NOT_FOUND);
        }

        Collection<ServiceStatus> servicesStatus = new ArrayList<>();
        for (Service s : this.services.getAll()) {
            if (name == null || name.equals(s.getName())) {
                servicesStatus.add(toServiceStatus(s));
            }
        }

        String res = gson.toJson(new ServiceStatusList(servicesStatus));
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    private ServiceStatus toServiceStatus(Service s) {
        return new ServiceStatus(s.getName(), s.getKeepAliveInterval().toSeconds(), s.timeSinceLastPing().toSeconds(),
                s.getCallbackUrl());
    }

    private void validateRegistrationInfo(ServiceRegistrationInfo registrationInfo)
            throws ServiceException, MalformedURLException {
        if (registrationInfo.serviceId.isEmpty()) {
            throw new ServiceException("Missing mandatory parameter 'service-id'");
        }
        if (registrationInfo.keepAliveIntervalSeconds < 0) {
            throw new ServiceException("Keepalive interval shoul be greater or equal to 0");
        }
        if (!registrationInfo.callbackUrl.isEmpty()) {
            new URL(registrationInfo.callbackUrl);
        }
    }

    private static final String REGISTER_SERVICE_DETAILS = "Registering a service is needed to:" //
            + "<ul>" //
            + "<li>Get callbacks.</li>" //
            + "<li>Activate supervision of the service. If a service is inactive, its policies will be deleted.</li>"//
            + "</ul>" //
    ;

    @ApiOperation(value = "Register a service", notes = REGISTER_SERVICE_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Service updated"),
            @ApiResponse(code = 201, message = "Service created"), //
            @ApiResponse(code = 400, message = "The ServiceRegistrationInfo is not accepted",
                    response = ErrorResponse.ErrorInfo.class)})
    @PutMapping(Consts.V2_API_ROOT + "/services")
    public ResponseEntity<Object> putService(//
            @RequestBody ServiceRegistrationInfo registrationInfo) {
        try {
            validateRegistrationInfo(registrationInfo);
            final boolean isCreate = this.services.get(registrationInfo.serviceId) == null;
            this.services.put(toService(registrationInfo));
            return new ResponseEntity<>(isCreate ? HttpStatus.CREATED : HttpStatus.OK);
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "Unregister a service")
    @ApiResponses(value = { //
            @ApiResponse(code = 204, message = "Service unregistered"),
            @ApiResponse(code = 200, message = "Not used", response = VoidResponse.class),
            @ApiResponse(code = 404, message = "Service not found", response = ErrorResponse.ErrorInfo.class)})
    @DeleteMapping(Consts.V2_API_ROOT + "/services/{serviceId}")
    public ResponseEntity<Object> deleteService(//
            @PathVariable("serviceId") String serviceId) {
        try {
            Service service = removeService(serviceId);
            // Remove the policies from the repo and let the consistency monitoring
            // do the rest.
            removePolicies(service);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ServiceException e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    @ApiOperation(value = "Heartbeat indicates that the service is running")
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Service supervision timer refreshed, OK"), //
            @ApiResponse(code = 404, message = "The service is not found, needs re-registration",
                    response = ErrorResponse.ErrorInfo.class)})

    @PutMapping(Consts.V2_API_ROOT + "/services/{service_id}/keepalive")
    public ResponseEntity<Object> keepAliveService(//
            @PathVariable(Consts.SERVICE_ID_PARAM) String serviceId) {
        try {
            services.getService(serviceId).keepAlive();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (ServiceException e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
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
        return new Service(s.serviceId, Duration.ofSeconds(s.keepAliveIntervalSeconds), s.callbackUrl);
    }

}
