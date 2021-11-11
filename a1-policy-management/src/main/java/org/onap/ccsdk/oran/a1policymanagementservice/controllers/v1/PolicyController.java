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

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.VoidResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.v2.ErrorResponse;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = Consts.V1_API_NAME)
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
    private static Gson gson = new GsonBuilder().create();

    @GetMapping("/policy_schemas") //
    @Operation(summary = "Returns policy type schema definitions") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy schemas", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
    })
    public ResponseEntity<String> getPolicySchemas( //
            @Parameter(name = "ric", required = false,
                    description = "The name of the Near-RT RIC to get the definitions for.") //
            @RequestParam(name = "ric", required = false) String ricName) throws EntityNotFoundException {
        if (ricName == null) {
            Collection<PolicyType> types = this.policyTypes.getAll();
            return new ResponseEntity<>(toPolicyTypeSchemasJson(types), HttpStatus.OK);
        } else {
            Collection<PolicyType> types = rics.getRic(ricName).getSupportedPolicyTypes();
            return new ResponseEntity<>(toPolicyTypeSchemasJson(types), HttpStatus.OK);
        }
    }

    @GetMapping("/policy_schema")
    @Operation(summary = "Returns one policy type schema definition")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy schema", //
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "404", //
                    description = "The policy type is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class)))//
    })
    public ResponseEntity<String> getPolicySchema( //
            @Parameter(name = "id", required = true,
                    description = "The identity of the policy type to get the definition for.") //
            @RequestParam(name = "id", required = true) String id) throws EntityNotFoundException {
        PolicyType type = policyTypes.getType(id);
        return new ResponseEntity<>(type.getSchema(), HttpStatus.OK);
    }

    @GetMapping("/policy_types")
    @Operation(summary = "Query policy type identities")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy type identities", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class)))//
    })
    public ResponseEntity<String> getPolicyTypes( //
            @Parameter(name = "ric", required = false, description = "The name of the Near-RT RIC to get types for.") //
            @RequestParam(name = "ric", required = false) String ricName) throws EntityNotFoundException {
        if (ricName == null) {
            Collection<PolicyType> types = this.policyTypes.getAll();
            return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
        } else {
            Collection<PolicyType> types = rics.getRic(ricName).getSupportedPolicyTypes();
            return new ResponseEntity<>(toPolicyTypeIdsJson(types), HttpStatus.OK);
        }
    }

    @GetMapping("/policy")
    @Operation(summary = "Returns a policy configuration") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy found", //
                    content = @Content(schema = @Schema(implementation = Object.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))//
            )} //
    )
    public ResponseEntity<String> getPolicy( //
            @Parameter(name = "id", required = true, description = "The identity of the policy instance.") //
            @RequestParam(name = "id", required = true) String id) throws EntityNotFoundException {
        Policy p = policies.getPolicy(id);
        return new ResponseEntity<>(p.getJson(), HttpStatus.OK);
    }

    @DeleteMapping("/policy")
    @Operation(summary = "Delete a policy")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Not used", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))),
            @ApiResponse(responseCode = "204", //
                    description = "Policy deleted", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))),
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "423", //
                    description = "Near-RT RIC is not operational", //
                    content = @Content(schema = @Schema(implementation = String.class)))})
    public Mono<ResponseEntity<Object>> deletePolicy( //
            @Parameter(name = "id", required = true, description = "The identity of the policy instance.") //
            @RequestParam(name = "id", required = true) String id) throws EntityNotFoundException {
        Policy policy = policies.getPolicy(id);
        keepServiceAlive(policy.getOwnerServiceId());
        Ric ric = policy.getRic();
        return ric.getLock().lock(LockType.SHARED) //
                .flatMap(notUsed -> assertRicStateIdle(ric)) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(policy.getRic())) //
                .doOnNext(notUsed -> policies.remove(policy)) //
                .flatMap(client -> client.deletePolicy(policy)) //
                .doOnNext(notUsed -> ric.getLock().unlockBlocking()) //
                .doOnError(notUsed -> ric.getLock().unlockBlocking()) //
                .map(notUsed -> new ResponseEntity<>(HttpStatus.NO_CONTENT)) //
                .onErrorResume(this::handleException);
    }

    @PutMapping(path = "/policy")
    @Operation(summary = "Put a policy")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "201", //
                    description = "Policy created", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "200", //
                    description = "Policy updated", //
                    content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(responseCode = "423", //
                    description = "Near-RT RIC is not operational", //
                    content = @Content(schema = @Schema(implementation = String.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC or policy type is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })
    public Mono<ResponseEntity<Object>> putPolicy( //
            @Parameter(name = "type", required = false, description = "The name of the policy type.") //
            @RequestParam(name = "type", required = false, defaultValue = "") String typeName, //
            @Parameter(name = "id", required = true, description = "The identity of the policy instance.") //
            @RequestParam(name = "id", required = true) String instanceId, //
            @Parameter(name = "ric", required = true,
                    description = "The name of the Near-RT RIC where the policy will be " + //
                            "created.") //
            @RequestParam(name = "ric", required = true) String ricName, //
            @Parameter(name = "service", required = true, description = "The name of the service creating the policy.") //
            @RequestParam(name = "service", required = true) String service, //
            @Parameter(name = "transient", required = false,
                    description = "If the policy is transient or not (boolean " + //
                            "defaulted to false). A policy is transient if it will be forgotten when the service needs to "
                            + //
                            "reconnect to the Near-RT RIC.") //
            @RequestParam(name = "transient", required = false, defaultValue = "false") boolean isTransient, //
            @RequestBody Object jsonBody) {

        String jsonString = gson.toJson(jsonBody);
        Ric ric = rics.get(ricName);
        PolicyType type = policyTypes.get(typeName);
        keepServiceAlive(service);
        if (ric == null || type == null) {
            return Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        }
        Policy policy = Policy.builder() //
                .id(instanceId) //
                .json(jsonString) //
                .type(type) //
                .ric(ric) //
                .ownerServiceId(service) //
                .lastModified(Instant.now()) //
                .isTransient(isTransient) //
                .statusNotificationUri("") //
                .build();

        final boolean isCreate = this.policies.get(policy.getId()) == null;

        return ric.getLock().lock(LockType.SHARED) //
                .flatMap(notUsed -> assertRicStateIdle(ric)) //
                .flatMap(notUsed -> checkSupportedType(ric, type)) //
                .flatMap(notUsed -> validateModifiedPolicy(policy)) //
                .flatMap(notUsed -> a1ClientFactory.createA1Client(ric)) //
                .flatMap(client -> client.putPolicy(policy)) //
                .doOnNext(notUsed -> policies.put(policy)) //
                .doOnNext(notUsed -> ric.getLock().unlockBlocking()) //
                .doOnError(trowable -> ric.getLock().unlockBlocking()) //
                .map(notUsed -> new ResponseEntity<>(isCreate ? HttpStatus.CREATED : HttpStatus.OK)) //
                .onErrorResume(this::handleException);
    }

    @SuppressWarnings({"unchecked"})
    private <T> Mono<ResponseEntity<T>> createResponseEntity(String message, HttpStatus status) {
        ResponseEntity<T> re = new ResponseEntity<>((T) message, status);
        return Mono.just(re);
    }

    private <T> Mono<ResponseEntity<T>> handleException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            return createResponseEntity(e.getResponseBodyAsString(), e.getStatusCode());
        } else if (throwable instanceof RejectionException) {
            RejectionException e = (RejectionException) throwable;
            return createResponseEntity(e.getMessage(), e.getStatus());
        } else {
            return createResponseEntity(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Object> validateModifiedPolicy(Policy policy) {
        // Check that ric is not updated
        Policy current = this.policies.get(policy.getId());
        if (current != null && !current.getRic().id().equals(policy.getRic().id())) {
            RejectionException e = new RejectionException("Policy cannot change RIC, policyId: " + current.getId() + //
                    ", RIC name: " + current.getRic().id() + //
                    ", new name: " + policy.getRic().id(), HttpStatus.CONFLICT);
            logger.debug("Request rejected, {}", e.getMessage());
            return Mono.error(e);
        }
        return Mono.just("OK");
    }

    private Mono<Object> checkSupportedType(Ric ric, PolicyType type) {
        if (!ric.isSupportingType(type.getId())) {
            logger.debug("Request rejected, type not supported, RIC: {}", ric);
            RejectionException e = new RejectionException(
                    "Type: " + type.getId() + " not supported by RIC: " + ric.id(), HttpStatus.NOT_FOUND);
            return Mono.error(e);
        }
        return Mono.just("OK");
    }

    private Mono<Object> assertRicStateIdle(Ric ric) {
        if (ric.getState() == Ric.RicState.AVAILABLE) {
            return Mono.just("OK");
        } else {
            logger.debug("Request rejected RIC not IDLE, ric: {}", ric);
            RejectionException e = new RejectionException(
                    "Ric is not operational, RIC name: " + ric.id() + ", state: " + ric.getState(), HttpStatus.LOCKED);
            return Mono.error(e);
        }
    }

    @GetMapping("/policies")
    @Operation(summary = "Query policies")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policies", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PolicyInfo.class)))), //
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC or type not found", //
                    content = @Content(schema = @Schema(implementation = String.class)))})
    public ResponseEntity<String> getPolicies( //
            @Parameter(name = "type", required = false,
                    description = "The name of the policy type to get policies for.") //
            @RequestParam(name = "type", required = false) String type, //
            @Parameter(name = "ric", required = false, description = "The name of the Near-RT RIC to get policies for.") //
            @RequestParam(name = "ric", required = false) String ric, //
            @Parameter(name = "service", required = false, description = "The name of the service to get policies for.") //
            @RequestParam(name = "service", required = false) String service) //
    {
        if ((type != null && this.policyTypes.get(type) == null)) {
            return new ResponseEntity<>("Policy type not found", HttpStatus.NOT_FOUND);
        }
        if ((ric != null && this.rics.get(ric) == null)) {
            return new ResponseEntity<>("Near-RT RIC not found", HttpStatus.NOT_FOUND);
        }

        String filteredPolicies = policiesToJson(policies.filterPolicies(type, ric, service, null));
        return new ResponseEntity<>(filteredPolicies, HttpStatus.OK);
    }

    @GetMapping("/policy_ids")
    @Operation(summary = "Query policies, only policy identities returned")
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy identitiess", //
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
            @ApiResponse(responseCode = "404", //
                    description = "Near-RT RIC or type not found", //
                    content = @Content(schema = @Schema(implementation = String.class)))})
    public ResponseEntity<String> getPolicyIds( //
            @Parameter(name = "type", required = false,
                    description = "The name of the policy type to get policies for.") //
            @RequestParam(name = "type", required = false) String type, //
            @Parameter(name = "ric", required = false, description = "The name of the Near-RT RIC to get policies for.") //
            @RequestParam(name = "ric", required = false) String ric, //
            @Parameter(name = "service", required = false, description = "The name of the service to get policies for.") //
            @RequestParam(name = "service", required = false) String service) //
    {
        if ((type != null && this.policyTypes.get(type) == null)) {
            return new ResponseEntity<>("Policy type not found", HttpStatus.NOT_FOUND);
        }
        if ((ric != null && this.rics.get(ric) == null)) {
            return new ResponseEntity<>("Near-RT RIC not found", HttpStatus.NOT_FOUND);
        }

        String policyIdsJson = toPolicyIdsJson(policies.filterPolicies(type, ric, service, null));
        return new ResponseEntity<>(policyIdsJson, HttpStatus.OK);
    }

    @GetMapping("/policy_status")
    @Operation(summary = "Returns a policy status") //
    @ApiResponses(value = { //
            @ApiResponse(responseCode = "200", //
                    description = "Policy status", //
                    content = @Content(schema = @Schema(implementation = Object.class))), //
            @ApiResponse(responseCode = "404", //
                    description = "Policy is not found", //
                    content = @Content(schema = @Schema(implementation = String.class))) //
    })
    public Mono<ResponseEntity<String>> getPolicyStatus( //
            @Parameter(name = "id", required = true, description = "The identity of the policy.") @RequestParam(
                    name = "id", //
                    required = true) String id)
            throws EntityNotFoundException {
        Policy policy = policies.getPolicy(id);

        return a1ClientFactory.createA1Client(policy.getRic()) //
                .flatMap(client -> client.getPolicyStatus(policy)) //
                .map(status -> new ResponseEntity<>(status, HttpStatus.OK)) //
                .onErrorResume(this::handleException);
    }

    private void keepServiceAlive(String name) {
        Service s = this.services.get(name);
        if (s != null) {
            s.keepAlive();
        }
    }

    private String policiesToJson(Collection<Policy> policies) {
        List<PolicyInfo> v = new ArrayList<>(policies.size());
        for (Policy p : policies) {
            PolicyInfo policyInfo = new PolicyInfo();
            policyInfo.id = p.getId();
            policyInfo.json = fromJson(p.getJson());
            policyInfo.ric = p.getRic().id();
            policyInfo.type = p.getType().getId();
            policyInfo.service = p.getOwnerServiceId();
            policyInfo.lastModified = p.getLastModified().toString();
            if (!policyInfo.validate()) {
                logger.error("BUG, all fields must be set");
            }
            v.add(policyInfo);
        }
        return gson.toJson(v);
    }

    private Object fromJson(String jsonStr) {
        return gson.fromJson(jsonStr, Object.class);
    }

    private String toPolicyTypeSchemasJson(Collection<PolicyType> types) {
        StringBuilder result = new StringBuilder();
        result.append("[");
        boolean first = true;
        for (PolicyType t : types) {
            if (!first) {
                result.append(",");
            }
            first = false;
            result.append(t.getSchema());
        }
        result.append("]");
        return result.toString();
    }

    private String toPolicyTypeIdsJson(Collection<PolicyType> types) {
        List<String> v = new ArrayList<>(types.size());
        for (PolicyType t : types) {
            v.add(t.getId());
        }
        return gson.toJson(v);
    }

    private String toPolicyIdsJson(Collection<Policy> policies) {
        List<String> v = new ArrayList<>(policies.size());
        for (Policy p : policies) {
            v.add(p.getId());
        }
        return gson.toJson(v);
    }

}
