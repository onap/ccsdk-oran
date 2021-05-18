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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
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
@Tag(name = PolicyController.API_NAME)
public class PolicyController {

    public static final String API_NAME = "A1 Policy Management";
    public static final String API_DESCRIPTION = "";

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

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-types/{policytype_id:.+}") //
    @Operation(summary = "Returns a policy type definition") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy type", //
                    content = @Content(schema = @Schema(implementation = PolicyTypeInfo.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy type is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class)))//
    })
    public ResponseEntity<Object> getPolicyType( //
            @PathVariable("policytype_id") String policyTypeId) throws EntityNotFoundException {
        var type = policyTypes.getType(policyTypeId);
        var info = new PolicyTypeInfo(type.getSchema());
        return new ResponseEntity<>(gson.toJson(info), HttpStatus.OK);
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-types", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query policy type identities")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy type IDs", //
                    content = @Content(schema = @Schema(implementation = PolicyTypeIdList.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getPolicyTypes( //
            @Parameter(name = Consts.RIC_ID_PARAM, required = false,
                    description = "The identity of the Near-RT RIC to get types for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId) throws EntityNotFoundException {
        if (ricId == null) {
            Collection<PolicyType> types = this.policyTypes.getAll();
            return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
        } else {
            Collection<PolicyType> types = rics.getRic(ricId).getSupportedPolicyTypes();
            return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
        }
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies/{policy_id:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns a policy") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy found", //
                    content = @Content(schema = @Schema(implementation = PolicyInfo.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getPolicy( //
            @PathVariable(name = Consts.POLICY_ID_PARAM, required = true) String id) throws EntityNotFoundException {
        var policy = policies.getPolicy(id);
        return new ResponseEntity<>(gson.toJson(toPolicyInfo(policy)), HttpStatus.OK);
    }

    @DeleteMapping(Consts.V2_API_ROOT + "/policies/{policy_id:.+}")
    @Operation(summary = "Delete a policy")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Not used", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "204", //
                    description = "Policy deleted", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))), //
            @ApiResponse(responseCode = "423", //
                    description = "Near-RT RIC is not operational", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public Mono<ResponseEntity<Object>> deletePolicy( //
            @PathVariable(Consts.POLICY_ID_PARAM) String policyId) throws EntityNotFoundException {
        var policy = policies.getPolicy(policyId);
        keepServiceAlive(policy.getOwnerServiceId());
        var ric = policy.getRic();

        return ric.getLock().lock(LockType.SHARED) //
                .flatMap(notUsed -> assertRicStateIdle(ric)) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(policy.getRic())) //
                .doOnNext(notUsed -> policies.remove(policy)) //
                .flatMap(client -> client.deletePolicy(policy)) //
                .doOnNext(notUsed -> ric.getLock().unlockBlocking()) //
                .doOnError(notUsed -> ric.getLock().unlockBlocking()) //
                .flatMap(notUsed -> Mono.just(new ResponseEntity<>(HttpStatus.NO_CONTENT)))
                .onErrorResume(this::handleException);
    }

    @PutMapping(path = Consts.V2_API_ROOT + "/policies", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a policy")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "201", //
                    description = "Policy created", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "200", //
                    description = "Policy updated", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "423", //
                    description = "Near-RT RIC is not operational", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC or policy type is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public Mono<ResponseEntity<Object>> putPolicy(@RequestBody PolicyInfo policyInfo) throws EntityNotFoundException {

        if (!policyInfo.validate()) {
            return ErrorResponse.createMono("Missing required parameter in body", HttpStatus.BAD_REQUEST);
        }
        var jsonString = gson.toJson(policyInfo.policyData);
        var ric = rics.get(policyInfo.ricId);
        var type = policyTypes.get(policyInfo.policyTypeId);
        keepServiceAlive(policyInfo.serviceId);
        if (ric == null || type == null) {
            throw new EntityNotFoundException("Near-RT RIC or policy type not found");
        }
        var policy = Policy.builder() //
                .id(policyInfo.policyId) //
                .json(jsonString) //
                .type(type) //
                .ric(ric) //
                .ownerServiceId(policyInfo.serviceId) //
                .lastModified(Instant.now()) //
                .isTransient(policyInfo.isTransient) //
                .statusNotificationUri(policyInfo.statusNotificationUri == null ? "" : policyInfo.statusNotificationUri) //
                .build();

        final var isCreate = this.policies.get(policy.getId()) == null;

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
        var current = this.policies.get(policy.getId());
        if (current != null && !current.getRic().id().equals(policy.getRic().id())) {
            RejectionException e = new RejectionException("Policy cannot change RIC, policyId: " + current.getId() + //
                    ", RIC ID: " + current.getRic().id() + //
                    ", new ID: " + policy.getRic().id(), HttpStatus.CONFLICT);
            logger.debug("Request rejected, {}", e.getMessage());
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private Mono<Object> checkSupportedType(Ric ric, PolicyType type) {
        if (!ric.isSupportingType(type.getId())) {
            logger.debug("Request rejected, type not supported, RIC: {}", ric);
            var e = new RejectionException("Type: " + type.getId() + " not supported by RIC: " + ric.id(),
                    HttpStatus.NOT_FOUND);
            return Mono.error(e);
        }
        return Mono.just("{}");
    }

    private Mono<Object> assertRicStateIdle(Ric ric) {
        if (ric.getState() == Ric.RicState.AVAILABLE) {
            return Mono.just("{}");
        } else {
            logger.debug("Request rejected Near-RT RIC not IDLE, ric: {}", ric);
            var e = new RejectionException(
                    "Near-RT RIC: is not operational, id: " + ric.id() + ", state: " + ric.getState(),
                    HttpStatus.LOCKED);
            return Mono.error(e);
        }
    }

    static final String GET_POLICIES_QUERY_DETAILS =
            "Returns a list of A1 policies matching given search criteria. <br>" //
                    + "If several query parameters are defined, the policies matching all conditions are returned.";

    @GetMapping(path = Consts.V2_API_ROOT + "/policy-instances", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query for A1 policy instances", description = GET_POLICIES_QUERY_DETAILS)
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policies", //
                    content = @Content(schema = @Schema(implementation = PolicyInfoList.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC, policy type or service not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getPolicyInstances( //
            @Parameter(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    description = "The identity of the policy type to get policies for.") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String type, //
            @Parameter(name = Consts.RIC_ID_PARAM, required = false,
                    description = "The identity of the Near-RT RIC to get policies for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ric, //
            @Parameter(name = Consts.SERVICE_ID_PARAM, required = false,
                    description = "The identity of the service to get policies for.") //
            @RequestParam(name = Consts.SERVICE_ID_PARAM, required = false) String service)
            throws EntityNotFoundException //
    {
        if ((type != null && this.policyTypes.get(type) == null)) {
            throw new EntityNotFoundException("Policy type not found");
        }
        if ((ric != null && this.rics.get(ric) == null)) {
            throw new EntityNotFoundException("Near-RT RIC not found");
        }

        var filteredPolicies = policiesToJson(filter(type, ric, service));
        return new ResponseEntity<>(filteredPolicies, HttpStatus.OK);
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies", produces = MediaType.APPLICATION_JSON_VALUE) //
    @Operation(summary = "Query policy identities", description = GET_POLICIES_QUERY_DETAILS) //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy identities", //
                    content = @Content(schema = @Schema(implementation = PolicyIdList.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC or type not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<Object> getPolicyIds( //
            @Parameter(name = Consts.POLICY_TYPE_ID_PARAM, required = false,
                    description = "The identity of the policy type to get policies for.") //
            @RequestParam(name = Consts.POLICY_TYPE_ID_PARAM, required = false) String policyTypeId, //
            @Parameter(name = Consts.RIC_ID_PARAM, required = false,
                    description = "The identity of the Near-RT RIC to get policies for.") //
            @RequestParam(name = Consts.RIC_ID_PARAM, required = false) String ricId, //
            @Parameter(name = Consts.SERVICE_ID_PARAM, required = false,
                    description = "The identity of the service to get policies for.") //
            @RequestParam(name = Consts.SERVICE_ID_PARAM, required = false) String serviceId)
            throws EntityNotFoundException //
    {
        if ((policyTypeId != null && this.policyTypes.get(policyTypeId) == null)) {
            throw new EntityNotFoundException("Policy type not found");
        }
        if ((ricId != null && this.rics.get(ricId) == null)) {
            throw new EntityNotFoundException("Near-RT RIC not found");
        }

        var policyIdsJson = toPolicyIdsJson(filter(policyTypeId, ricId, serviceId));
        return new ResponseEntity<>(policyIdsJson, HttpStatus.OK);
    }

    @GetMapping(path = Consts.V2_API_ROOT + "/policies/{policy_id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns a policy status") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy status", //
                    content = @Content(schema = @Schema(implementation = PolicyStatusInfo.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public Mono<ResponseEntity<Object>> getPolicyStatus( //
            @PathVariable(Consts.POLICY_ID_PARAM) String policyId) throws EntityNotFoundException {
        var policy = policies.getPolicy(policyId);

        return a1ClientFactory.createA1Client(policy.getRic()) //
                .flatMap(client -> client.getPolicyStatus(policy).onErrorResume(e -> Mono.just("{}"))) //
                .flatMap(status -> createPolicyStatus(policy, status)) //
                .onErrorResume(this::handleException);

    }

    private Mono<ResponseEntity<Object>> createPolicyStatus(Policy policy, String statusFromNearRic) {
        var info = new PolicyStatusInfo(policy.getLastModified(), fromJson(statusFromNearRic));
        var str = gson.toJson(info);
        return Mono.just(new ResponseEntity<>(str, HttpStatus.OK));
    }

    private void keepServiceAlive(String name) {
        var service = this.services.get(name);
        if (service != null) {
            service.keepAlive();
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
        for (var policy : collection) {
            if (include(type, policy.getType().getId()) && include(ric, policy.getRic().id())
                    && include(service, policy.getOwnerServiceId())) {
                filtered.add(policy);
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
        var policyInfo = new PolicyInfo();
        policyInfo.policyId = p.getId();
        policyInfo.policyData = fromJson(p.getJson());
        policyInfo.ricId = p.getRic().id();
        policyInfo.policyTypeId = p.getType().getId();
        policyInfo.serviceId = p.getOwnerServiceId();
        policyInfo.isTransient = p.isTransient();
        if (!p.getStatusNotificationUri().isEmpty()) {
            policyInfo.statusNotificationUri = p.getStatusNotificationUri();
        }
        if (!policyInfo.validate()) {
            logger.error("BUG, all mandatory fields must be set");
        }

        return policyInfo;
    }

    private String policiesToJson(Collection<Policy> policies) {
        List<PolicyInfo> v = new ArrayList<>(policies.size());
        for (var policy : policies) {
            v.add(toPolicyInfo(policy));
        }
        var list = new PolicyInfoList(v);
        return gson.toJson(list);
    }

    private Object fromJson(String jsonStr) {
        return gson.fromJson(jsonStr, Object.class);
    }

    private String toPolicyTypeIdsJson(Collection<PolicyType> types) {
        List<String> v = new ArrayList<>(types.size());
        for (var type : types) {
            v.add(type.getId());
        }
        PolicyTypeIdList ids = new PolicyTypeIdList(v);
        return gson.toJson(ids);
    }

    private String toPolicyIdsJson(Collection<Policy> policies) {
        List<String> v = new ArrayList<>(policies.size());
        for (var policy : policies) {
            v.add(policy.getId());
        }
        return gson.toJson(new PolicyIdList(v));
    }

}
