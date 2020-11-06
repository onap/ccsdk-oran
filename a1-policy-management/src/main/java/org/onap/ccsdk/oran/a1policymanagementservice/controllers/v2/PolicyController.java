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

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController("PolicyControllerV2")
@Api(tags = {Consts.V2_API_NAME})
public class PolicyController {

    public static class RejectionException extends Exception {
        private static final long serialVersionUID = 1L;

        @Getter
        private final HttpStatus status;

        public RejectionException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }
    }

    @Autowired
    private Rics rics;
    @Autowired
    private PolicyTypes policyTypes;
    @Autowired
    private Policies policies;
    @Autowired
    private A1ClientFactory a1ClientFactory;
    @Autowired
    private Services services;

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static Gson gson = new GsonBuilder() //
            .create(); //

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-types/{policytype_id:.+}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Returns a policy type definition")
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Policy type", response = PolicyTypeInfo.class), //
            @ApiResponse(code = 404, message = "Policy type is not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getPolicyType( //
            @PathVariable("policytype_id") String policyTypeId) {
        try {
            PolicyType type = policyTypes.getType(policyTypeId);
            PolicyTypeInfo info = new PolicyTypeInfo(type.schema());
            return new ResponseEntity<>(gson.toJson(info), HttpStatus.OK);
        } catch (ServiceException e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-types", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Query policy type identities", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Policy type IDs", response = PolicyTypeIdList.class),
            @ApiResponse(code = 404, message = "Near-RT RIC is not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getPolicyTypes( //
            @ApiParam(name = Consts.RIC_ID_PARAM, required = false,
                    value = "The identity of the Near-RT RIC to get types for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId) {
        if (ricId == null) {
            Collection<PolicyType> types = this.policyTypes.getAll();
            return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
        } else {
            try {
                Collection<PolicyType> types = rics.getRic(ricId).getSupportedPolicyTypes();
                return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
            } catch (ServiceException e) {
                return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
            }
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies/{policy_id:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Returns a policy") //
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Policy found", response = PolicyInfo.class), //
            @ApiResponse(code = 404, message = "Policy is not found", response = ErrorResponse.ErrorInfo.class)} //
    )
    public ResponseEntity<Object> getPolicy( //
            @PathVariable(name = Consts.POLICY_ID_PARAM, required = true) String id) {
        try {
            Policy p = policies.getPolicy(id);
            return new ResponseEntity<>(gson.toJson(toPolicyInfo(p)), HttpStatus.OK);
        } catch (ServiceException e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping(Consts.V2_API_ROOT + "/policies/{policy_id:.+}")
    @ApiOperation(value = "Delete a policy")
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Not used", response = VoidResponse.class),
            @ApiResponse(code = 204, message = "Policy deleted", response = VoidResponse.class),
            @ApiResponse(code = 404, message = "Policy is not found", response = ErrorResponse.ErrorInfo.class),
            @ApiResponse(code = 423, message = "Near-RT RIC is not operational",
                    response = ErrorResponse.ErrorInfo.class)})
    public Mono<ResponseEntity<Object>> deletePolicy( //
            @PathVariable(Consts.POLICY_ID_PARAM) String policyId) {
        try {
            Policy policy = policies.getPolicy(policyId);
            keepServiceAlive(policy.ownerServiceId());
            Ric ric = policy.ric();
            return ric.getLock().lock(LockType.SHARED) //
                    .flatMap(notUsed -> assertRicStateIdle(ric)) //
                    .flatMap(notUsed -> a1ClientFactory.createA1Client(policy.ric())) //
                    .doOnNext(notUsed -> policies.remove(policy)) //
                    .flatMap(client -> client.deletePolicy(policy)) //
                    .doOnNext(notUsed -> ric.getLock().unlockBlocking()) //
                    .doOnError(notUsed -> ric.getLock().unlockBlocking()) //
                    .flatMap(notUsed -> Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)))
                    .onErrorResume(this::handleException);
        } catch (ServiceException e) {
            return ErrorResponse.createMono(e, HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping(path = Consts.V2_API_ROOT + "/policies", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Create or update a policy")
    @ApiResponses(value = { //
            @ApiResponse(code = 201, message = "Policy created", response = VoidResponse.class), //
            @ApiResponse(code = 200, message = "Policy updated", response = VoidResponse.class), //
            @ApiResponse(code = 423, message = "Near-RT RIC is not operational",
                    response = ErrorResponse.ErrorInfo.class), //
            @ApiResponse(code = 404, message = "Near-RT RIC or policy type is not found",
                    response = ErrorResponse.ErrorInfo.class) //
    })
    public Mono<ResponseEntity<Object>> putPolicy(@RequestBody PolicyInfo policyInfo) {

        if (!policyInfo.validate()) {
            return ErrorResponse.createMono("Missing required parameter in body", HttpStatus.BAD_REQUEST);
        }
        String jsonString = gson.toJson(policyInfo.policyData);
        Ric ric = rics.get(policyInfo.ricId);
        PolicyType type = policyTypes.get(policyInfo.policyTypeId);
        keepServiceAlive(policyInfo.serviceId);
        if (ric == null || type == null) {
            return ErrorResponse.createMono("Near-RT RIC or policy type not found", HttpStatus.NOT_FOUND);
        }
        Policy policy = ImmutablePolicy.builder() //
                .id(policyInfo.policyId) //
                .json(jsonString) //
                .type(type) //
                .ric(ric) //
                .ownerServiceId(policyInfo.serviceId) //
                .lastModified(Instant.now()) //
                .isTransient(policyInfo.isTransient) //
                .statusNotificationUri(policyInfo.statusNotificationUri == null ? "" : policyInfo.statusNotificationUri) //
                .build();

        final boolean isCreate = this.policies.get(policy.id()) == null;

        return ric.getLock().lock(LockType.SHARED) //
                .flatMap(notUsed -> assertRicStateIdle(ric)) //
                .flatMap(notUsed -> checkSupportedType(ric, type)) //
                .flatMap(notUsed -> validateModifiedPolicy(policy)) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(ric)) //
                .flatMap(client -> client.putPolicy(policy)) //
                .doOnNext(notUsed -> policies.put(policy)) //
                .doOnNext(notUsed -> ric.getLock().unlockBlocking()) //
                .doOnError(trowable -> ric.getLock().unlockBlocking()) //
                .flatMap(notUsed -> Mono.just(new ResponseEntity<>(isCreate ? HttpStatus.CREATED : HttpStatus.OK))) //
                .onErrorResume(this::handleException);
    }

    private Mono<ResponseEntity<Object>> handleException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            return ErrorResponse.createMono(e.getResponseBodyAsString(), e.getStatusCode());
        } else if (throwable instanceof RejectionException) {
            RejectionException e = (RejectionException) throwable;
            return ErrorResponse.createMono(e.getMessage(), e.getStatus());
        } else {
            return ErrorResponse.createMono(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Object> validateModifiedPolicy(Policy policy) {
        // Check that ric is not updated
        Policy current = this.policies.get(policy.id());
        if (current != null && !current.ric().id().equals(policy.ric().id())) {
            RejectionException e = new RejectionException("Policy cannot change RIC, policyId: " + current.id() + //
                    ", RIC ID: " + current.ric().id() + //
                    ", new ID: " + policy.ric().id(), HttpStatus.CONFLICT);
            logger.debug("Request rejected, {}", e.getMessage());
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private Mono<Object> checkSupportedType(Ric ric, PolicyType type) {
        if (!ric.isSupportingType(type.id())) {
            logger.debug("Request rejected, type not supported, RIC: {}", ric);
            RejectionException e = new RejectionException("Type: " + type.id() + " not supported by RIC: " + ric.id(),
                    HttpStatus.NOT_FOUND);
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private void assertRicStateIdleSync(Ric ric) throws ServiceException {
        if (ric.getState() != Ric.RicState.AVAILABLE) {
            throw new ServiceException("Near-RT RIC: " + ric.id() + " is " + ric.getState());
        }
    }

    private Mono<Object> assertRicStateIdle(Ric ric) {
        if (ric.getState() == Ric.RicState.AVAILABLE) {
            return Mono.just("{}");
        } else {
            logger.debug("Request rejected Near-RT RIC not IDLE, ric: {}", ric);
            RejectionException e = new RejectionException(
                    "Near-RT RIC: is not operational, id: " + ric.id() + ", state: " + ric.getState(),
                    HttpStatus.LOCKED);
            return Mono.error(e);
        }
    }

    static final String GET_POLICIES_QUERY_DETAILS =
            "Returns a list of A1 policies matching given search criteria. <br>" //
                    + "If several query parameters are defined, the policies matching all conditions are returned.";

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-instances", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Query for A1 policy instances", notes = GET_POLICIES_QUERY_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Policies", response = PolicyInfoList.class),
            @ApiResponse(code = 404, message = "Near-RT RIC, policy type or service not found",
                    response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getPolicyInstances( //
            @ApiParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    value = "The identity of the policy type to get policies for.") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String type, //
            @ApiParam(name = Consts.RIC_ID_PARAM, required = false,
                    value = "The identity of the Near-RT RIC to get policies for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ric, //
            @ApiParam(name = Consts.SERVICE_ID_PARAM, required = false,
                    value = "The identity of the service to get policies for.") //
            @RequestParam(name = Consts.SERVICE_ID_PARAM, required = false) String service) //
    {
        if ((type != null && this.policyTypes.get(type) == null)) {
            return ErrorResponse.create("Policy type not found", HttpStatus.NOT_FOUND);
        }
        if ((ric != null && this.rics.get(ric) == null)) {
            return ErrorResponse.create("Near-RT RIC not found", HttpStatus.NOT_FOUND);
        }

        String filteredPolicies = policiesToJson(filter(type, ric, service));
        return new ResponseEntity<>(filteredPolicies, HttpStatus.OK);
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Query policy identities", notes = GET_POLICIES_QUERY_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Policy identities", response = PolicyIdList.class), @ApiResponse(
                    code = 404, message = "Near-RT RIC or type not found", response = ErrorResponse.ErrorInfo.class)})
    public ResponseEntity<Object> getPolicyIds( //
            @ApiParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    value = "The identity of the policy type to get policies for.") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String policyTypeId, //
            @ApiParam(name = Consts.RIC_ID_PARAM, required = false,
                    value = "The identity of the Near-RT RIC to get policies for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId, //
            @ApiParam(name = Consts.SERVICE_ID_PARAM, required = false,
                    value = "The identity of the service to get policies for.") //
            @RequestParam(name = Consts.SERVICE_ID_PARAM, required = false) String serviceId) //
    {
        if ((policyTypeId != null && this.policyTypes.get(policyTypeId) == null)) {
            return ErrorResponse.create("Policy type not found", HttpStatus.NOT_FOUND);
        }
        if ((ricId != null && this.rics.get(ricId) == null)) {
            return ErrorResponse.create("Near-RT RIC not found", HttpStatus.NOT_FOUND);
        }

        String policyIdsJson = toPolicyIdsJson(filter(policyTypeId, ricId, serviceId));
        return new ResponseEntity<>(policyIdsJson, HttpStatus.OK);
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies/{policy_id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Returns a policy status") //
    @ApiResponses(value = { //
            @ApiResponse(code = 200, message = "Policy status", response = PolicyStatusInfo.class), //
            @ApiResponse(code = 404, message = "Policy is not found", response = ErrorResponse.ErrorInfo.class)} //
    )
    public Mono<ResponseEntity<Object>> getPolicyStatus( //
            @PathVariable(Consts.POLICY_ID_PARAM) String policyId) {
        try {
            Policy policy = policies.getPolicy(policyId);

            return a1ClientFactory.createA1Client(policy.ric()) //
                    .flatMap(client -> client.getPolicyStatus(policy).onErrorResume(e -> Mono.just("{}"))) //
                    .flatMap(status -> createPolicyStatus(policy, status)) //
                    .onErrorResume(this::handleException);
        } catch (ServiceException e) {
            return ErrorResponse.createMono(e, HttpStatus.NOT_FOUND);
        }
    }

    private Mono<ResponseEntity<Object>> createPolicyStatus(Policy policy, String statusFromNearRic) {
        PolicyStatusInfo info = new PolicyStatusInfo(policy.lastModified(), fromJson(statusFromNearRic));
        String str = gson.toJson(info);
        return Mono.just(new ResponseEntity<>((Object) str, HttpStatus.OK));
    }

    private void keepServiceAlive(String name) {
        Service s = this.services.get(name);
        if (s != null) {
            s.keepAlive();
        }
    }

    private boolean include(String filter, String value) {
        return filter == null || value.equals(filter);
    }

    private Collection<Policy> filter(Collection<Policy> collection, String type, String ric, String service) {
        if (type == null && ric == null && service == null) {
            return collection;
        }
        List<Policy> filtered = new ArrayList<>();
        for (Policy p : collection) {
            if (include(type, p.type().id()) && include(ric, p.ric().id()) && include(service, p.ownerServiceId())) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private Collection<Policy> filter(String type, String ric, String service) {
        if (type != null) {
            return filter(policies.getForType(type), null, ric, service);
        } else if (service != null) {
            return filter(policies.getForService(service), type, ric, null);
        } else if (ric != null) {
            return filter(policies.getForRic(ric), type, null, service);
        } else {
            return policies.getAll();
        }
    }

    private PolicyInfo toPolicyInfo(Policy p) {
        PolicyInfo policyInfo = new PolicyInfo();
        policyInfo.policyId = p.id();
        policyInfo.policyData = fromJson(p.json());
        policyInfo.ricId = p.ric().id();
        policyInfo.policyTypeId = p.type().id();
        policyInfo.serviceId = p.ownerServiceId();
        if (!p.statusNotificationUri().isEmpty()) {
            policyInfo.statusNotificationUri = p.statusNotificationUri();
        }
        if (!policyInfo.validate()) {
            logger.error("BUG, all mandatory fields must be set");
        }

        return policyInfo;
    }

    private String policiesToJson(Collection<Policy> policies) {
        List<PolicyInfo> v = new ArrayList<>(policies.size());
        for (Policy p : policies) {
            v.add(toPolicyInfo(p));
        }
        PolicyInfoList list = new PolicyInfoList(v);
        return gson.toJson(list);
    }

    private Object fromJson(String jsonStr) {
        return gson.fromJson(jsonStr, Object.class);
    }

    private String toPolicyTypeIdsJson(Collection<PolicyType> types) {
        List<String> v = new ArrayList<>(types.size());
        for (PolicyType t : types) {
            v.add(t.id());
        }
        PolicyTypeIdList ids = new PolicyTypeIdList(v);
        return gson.toJson(ids);
    }

    private String toPolicyIdsJson(Collection<Policy> policies) {
        List<String> v = new ArrayList<>(policies.size());
        for (Policy p : policies) {
            v.add(p.id());
        }
        return gson.toJson(new PolicyIdList(v));
    }

}
