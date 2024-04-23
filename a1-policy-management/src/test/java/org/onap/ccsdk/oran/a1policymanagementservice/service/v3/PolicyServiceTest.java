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

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.AuthorizationService;
import org.onap.ccsdk.oran.a1policymanagementservice.service.v3.PolicyService;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest()
@TestPropertySource(properties = { //
        "app.vardata-directory=/tmp/pmstestv3", //
})
public class PolicyServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
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

    @AfterEach
    public void clear() {
        policies.clear();
    }

    @AfterAll
    static void clearTestDir() {
        try {
            FileSystemUtils.deleteRecursively(Path.of("/tmp/pmstestv3"));
        } catch (Exception e) {
            logger.warn("Could test directory : {}", e.getMessage());
        }
    }

    @Test
    public void testPolicyAlreadyCreatedTrue() throws Exception{

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Policy policy = testHelper.buidTestPolicy(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), "122344-5674");
        when(helper.jsonSchemaValidation(any())).thenReturn(Boolean.TRUE);
        when(helper.buildPolicy(any(),any(), any(), any())).thenReturn(policy);
        when(helper.isPolicyAlreadyCreated(any(), any())).thenReturn(Mono.error(new ServiceException
                ("Same policy content already created with policy ID: 122344-5674", HttpStatus.BAD_REQUEST)));
        Mono<ResponseEntity<PolicyObjectInformation>> responseMono = policyService.createPolicyService(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), serverWebExchange);
        testHelper.verifyMockError(responseMono, "Same policy content already created with policy ID: 122344-5674");
    }

    @Test
    public void testPolicyNotAuthorizedFail() throws IOException {

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
