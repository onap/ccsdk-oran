/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2023 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationContextProvider;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest.Input.AccessType;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
@DependsOn(value = "ApplicationContextProvider.class")
public class AuthorizationCheck {

    private final ApplicationConfig applicationConfig;
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AsyncRestClient restClient;
    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public AuthorizationCheck(ApplicationConfig applicationConfig, SecurityContext securityContext) {

        this.applicationConfig = applicationConfig;
        AsyncRestClientFactory restClientFactory =
                new AsyncRestClientFactory(applicationConfig.getWebClientConfig(), securityContext);
        this.restClient = restClientFactory.createRestClientUseHttpProxy("");
    }

    public Mono<Policy> doAccessControl(Map<String, String> receivedHttpHeaders, Policy policy, AccessType accessType) {
        return doAccessControl(receivedHttpHeaders, policy.getType(), accessType) //
                .map(x -> policy);
    }

    public Mono<PolicyType> doAccessControl(Map<String, String> receivedHttpHeaders, PolicyType type,
            AccessType accessType) {
        if (this.applicationConfig.getAuthProviderUrl().isEmpty()) {
            return Mono.just(type);
        }

        String tkn = getAuthToken(receivedHttpHeaders);
        PolicyAuthorizationRequest.Input input = PolicyAuthorizationRequest.Input.builder() //
                .authToken(tkn) //
                .policyTypeId(type.getId()) //
                .accessType(accessType).build();

        PolicyAuthorizationRequest req = PolicyAuthorizationRequest.builder().input(input).build();

        String url = this.applicationConfig.getAuthProviderUrl();
        return this.restClient.post(url, gson.toJson(req)) //
                .doOnError(t -> logger.warn("Error returned from auth server: {}", t.getMessage())) //
                .onErrorResume(t -> Mono.just("")) //
                .flatMap(this::checkAuthResult) //
                .map(rsp -> type);

    }

    private String getAuthToken(Map<String, String> httpHeaders) {
        String tkn = httpHeaders.get("authorization");
        if (tkn == null) {
            logger.debug("No authorization token received in {}", httpHeaders);
            return "";
        }
        tkn = tkn.substring("Bearer ".length());
        return tkn;
    }

    private Mono<String> checkAuthResult(String response) {
        logger.debug("Auth result: {}", response);
        try {
            AuthorizationResult res = gson.fromJson(response, AuthorizationResult.class);
            return res != null && res.isResult() ? Mono.just(response)
                    : Mono.error(new ServiceException("Not authorized", HttpStatus.UNAUTHORIZED));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

}
