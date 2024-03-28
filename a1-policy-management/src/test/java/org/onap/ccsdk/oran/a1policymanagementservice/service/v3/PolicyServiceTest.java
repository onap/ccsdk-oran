/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2024 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.service.v3;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest()
public class PolicyServiceTest {

    @Autowired
    private Rics rics;

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    private Policies policies;

    @Autowired
    private A1ClientFactory a1ClientFactory;

    @Autowired
    private ErrorHandlingService errorHandlingService;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private TestHelper testHelper;

    @MockBean
    private Helper helper;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    public void testPolicyAlreadyCreatedFail() {

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        when(helper.isPolicyAlreadyCreated(any(), any())).thenReturn(Mono.error(new ServiceException
                ("Policy already created", HttpStatus.CONFLICT)));
        Mono<ResponseEntity<PolicyObjectInformation>> responseMono = policyService.createPolicyService(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), serverWebExchange);
        testHelper.verifyMockError(responseMono, "Policy already created");
    }

    @Test
    public void testPolicyNotAuthorizedFail() {

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        when(helper.isPolicyAlreadyCreated(any(), any())).thenReturn(Mono.just(Policy.builder().build()));
        when(authorizationService.authCheck(any(), any(), any())).thenReturn(Mono.error(new ServiceException("Not authorized", HttpStatus.UNAUTHORIZED)));
        Mono<ResponseEntity<PolicyObjectInformation>> responseMono = policyService.createPolicyService(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), serverWebExchange);
        testHelper.verifyMockError(responseMono, "Not authorized");
    }
}
