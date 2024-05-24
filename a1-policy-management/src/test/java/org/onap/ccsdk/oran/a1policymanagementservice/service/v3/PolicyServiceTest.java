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

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.onap.ccsdk.oran.a1policymanagementservice.config.TestConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyObjectInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v3.PolicyTypeInformation;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.util.v3.Helper;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.v3.TestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = { //
        "app.vardata-directory=/tmp/pmstestv3", //
})
public class PolicyServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private Policies policies;

    @Autowired
    private Rics rics;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private TestHelper testHelper;

    @MockBean
    private Helper helper;

    @MockBean
    private AuthorizationService authorizationService;

    @Autowired
    private MockA1ClientFactory a1ClientFactory;

    @Autowired
    private Gson gson;

    @AfterEach
    public void clear() {
        policies.clear();
        rics.clear();
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

    @Test
    public void testDeletePolicySuccess() throws Exception {

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        Policy policy = testHelper.buidTestPolicy(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), "122344-5674");
        policies.put(policy);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.checkRicStateIdle(any())).thenReturn(Mono.just(policy.getRic()));
        when(helper.checkSupportedType(any(), any())).thenReturn(Mono.just(policy.getRic()));
        when(authorizationService.authCheck(any(), any(), any())).thenReturn(Mono.just(policy));
        Mono<ResponseEntity<Void>> responseMonoDelete = policyService.deletePolicyService(policy.getId(), serverWebExchange);
        assert(policies.size() == 1);
        testHelper.testSuccessResponse(responseMonoDelete, HttpStatus.NO_CONTENT, responseBody -> policies.size() == 0);
    }

    @Test
    public void testDeletePolicyThrowsException() throws Exception {

        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        assertThrows(EntityNotFoundException.class, () -> policyService.deletePolicyService("dummyPolicyID", serverWebExchange));
    }

    @Test
    public void testPutPolicy() throws Exception {

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        Policy policy = testHelper.buidTestPolicy(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), "122344-5674");
        policies.put(policy);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        PolicyObjectInformation updatedPolicyObjectInfo = testHelper.policyObjectInfo(nonRtRicId, policyTypeName);
        updatedPolicyObjectInfo.setPolicyObject(gson.fromJson(JsonParser.parseString("{\n" +
                "        \"scope\": {\n" +
                "            \"ueId\": \"ue6100\",\n" +
                "            \"qosId\": \"qos6100\"\n" +
                "        },\n" +
                "        \"qosObjectives\": {\n" +
                "            \"priorityLevel\": 6100.0\n" +
                "        }\n" +
                "    }").getAsJsonObject().toString(), Map.class));
        Policy updatedPolicy = testHelper.buidTestPolicy(updatedPolicyObjectInfo, "122344-5674");
        when(helper.buildPolicy(any(),any(), any(), any())).thenReturn(updatedPolicy);
        when(helper.checkRicStateIdle(any())).thenReturn(Mono.just(updatedPolicy.getRic()));
        when(helper.checkSupportedType(any(), any())).thenReturn(Mono.just(updatedPolicy.getRic()));
        when(authorizationService.authCheck(any(), any(), any())).thenReturn(Mono.just(updatedPolicy));
        Mono<ResponseEntity<Object>> responseMono = policyService.putPolicyService(policy.getId(), updatedPolicyObjectInfo.getPolicyObject(), serverWebExchange);
        testHelper.testSuccessResponse(responseMono, HttpStatus.OK, responseBody -> {
            if (responseBody instanceof String returnPolicy)
                return returnPolicy.contains(updatedPolicy.getJson());
            return false;
        });
    }

    @Test
    public void testGetPolicyTypes() throws Exception {

        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService.getPolicyTypesService(null, null,null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().count() == 1);
    }

    @Test
    public void testGetPolicyTypesEmpty() throws Exception {
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService.getPolicyTypesService(null, null, null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().count() == 0);
    }

    @Test
    public void testGetPolicyTypesNoRic() {
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        assertThrows(EntityNotFoundException.class, () -> policyService.getPolicyTypesService("NoRic", "","", serverWebExchange));
    }

    @Test
    public void testGetPolicyTypesNoMatchedTypeName() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService.getPolicyTypesService("", "noTypeName", null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().findAny().isEmpty());
    }

    @Test
    public void testGetPolicyTypesNoMatchedTypeNameWithRic() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService.getPolicyTypesService("Ric_347", "noTypeName", null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().findAny().isEmpty());
    }

    @Test
    public void testGetPolicyTypesMatchedTypeName() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService.getPolicyTypesService(null, "uri_type", null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().count() == 1);
    }

    @Test
    public void testGetPolicyTypesMatchedTypeNameWithRic() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        when(helper.toPolicyTypeInfoCollection(any(), any())).thenCallRealMethod();
        Mono<ResponseEntity<Flux<PolicyTypeInformation>>> responseEntityMono = policyService
                .getPolicyTypesService("Ric_347", "uri_type", null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().count() == 1);
    }

    @Test
    public void testGetPolicyIds() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        Policy policy = testHelper.buidTestPolicy(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), "122344-5674");
        policies.put(policy);
        when(authorizationService.authCheck(any(), any(), any())).thenReturn(Mono.just(policy));
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Policy singlePolicy = policies.filterPolicies(null, null, null, null).iterator().next();
        Collection<PolicyInformation> mockPolicyInfoCollection = new ArrayList<>();
        mockPolicyInfoCollection.add(new PolicyInformation(singlePolicy.getId(), singlePolicy.getRic().getConfig().getRicId()));
        when(helper.toFluxPolicyInformation(any())).thenReturn(Flux.fromIterable(mockPolicyInfoCollection));
        Mono<ResponseEntity<Flux<PolicyInformation>>> responseEntityMono = policyService
                .getPolicyIdsService(null, null, null, null, serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> responseBody.toStream().count() == 1);
    }

    @Test
    public void testGetPolicyIdsNoRic() throws Exception {
        testHelper.addPolicyType("uri_type_123", "Ric_347");
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> policyService
                .getPolicyIdsService("uri_type_123", "noRic", "", "", serverWebExchange));
        assertEquals("Near-RT RIC not found using ID: noRic", exception.getMessage());
    }

    @Test
    public void testGetPolicyIdsNoPolicyType() {
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> policyService
                .getPolicyIdsService("noPolicyType", "noRic", "", "", serverWebExchange));
        assertEquals("Policy type not found using ID: noPolicyType", exception.getMessage());
    }

    @Test
    public void testGetPolicyService() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        testHelper.addPolicyType(policyTypeName, nonRtRicId);
        Policy policy = testHelper.buidTestPolicy(testHelper.policyObjectInfo(nonRtRicId, policyTypeName), "122344-5674");
        policies.put(policy);
        when(authorizationService.authCheck(any(), any(), any())).thenReturn(Mono.just(policy));
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        Mono<ResponseEntity<Object>> responseEntityMono = policyService.getPolicyService(policy.getId(), serverWebExchange);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> {
            if (responseBody instanceof String returnPolicy)
                return returnPolicy.contains(policy.getJson());
            return false;
        });
    }

    @Test
    public void testGetPolicyServiceNoPolicy() throws Exception {
        ServerWebExchange serverWebExchange = Mockito.mock(DefaultServerWebExchange.class);
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> policyService
                .getPolicyService("NoPolicy", serverWebExchange));
        assertEquals("Could not find policy: NoPolicy", exception.getMessage());
    }

    @Test
    public void testGetPolicyTypeService() throws Exception {
        String policyTypeName = "uri_type_123";
        String nonRtRicId = "Ric_347";
        PolicyType addedPolicyType = testHelper.addPolicyType(policyTypeName, nonRtRicId);
        Mono<ResponseEntity<Object>> responseEntityMono = policyService.getPolicyTypeDefinitionService(policyTypeName);
        testHelper.testSuccessResponse(responseEntityMono, HttpStatus.OK, responseBody -> {
            if (responseBody instanceof String returnPolicyType)
                return returnPolicyType.contains(addedPolicyType.getSchema());
            return false;
        });
    }

    @Test
    public void testGetPolicyTypeServiceNoPolicyType() {
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> policyService
                .getPolicyTypeDefinitionService("NoPolicyType"));
        assertEquals("PolicyType not found with ID: NoPolicyType", exception.getMessage());
    }
}
