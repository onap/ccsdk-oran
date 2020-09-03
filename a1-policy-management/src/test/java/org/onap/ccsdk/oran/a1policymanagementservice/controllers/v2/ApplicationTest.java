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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableWebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.ImmutablePolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RicSupervision;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.ServiceSupervision;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1Client;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.MockA1ClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.annotation.Nullable;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks"})
class ApplicationTest {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationTest.class);

    @Autowired
    ApplicationContext context;

    @Autowired
    private Rics rics;

    @Autowired
    private Policies policies;

    @Autowired
    private PolicyTypes policyTypes;

    @Autowired
    MockA1ClientFactory a1ClientFactory;

    @Autowired
    RicSupervision supervision;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    Services services;

    private static Gson gson = new GsonBuilder() //
            .serializeNulls() //
            .create(); //

    public static class MockApplicationConfig extends ApplicationConfig {
        @Override
        public String getLocalConfigurationFilePath() {
            return ""; // No config file loaded for the test
        }
    }

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory {
        private final PolicyTypes policyTypes = new PolicyTypes();
        private final Services services = new Services();
        private final Policies policies = new Policies();
        MockA1ClientFactory a1ClientFactory = null;

        @Bean
        public ApplicationConfig getApplicationConfig() {
            return new MockApplicationConfig();
        }

        @Bean
        MockA1ClientFactory getA1ClientFactory() {
            if (a1ClientFactory == null) {
                this.a1ClientFactory = new MockA1ClientFactory(this.policyTypes);
            }
            return this.a1ClientFactory;
        }

        @Bean
        public PolicyTypes getPolicyTypes() {
            return this.policyTypes;
        }

        @Bean
        Policies getPolicies() {
            return this.policies;
        }

        @Bean
        Services getServices() {
            return this.services;
        }

        @Bean
        public ServiceSupervision getServiceSupervision() {
            Duration checkInterval = Duration.ofMillis(1);
            return new ServiceSupervision(this.services, this.policies, this.getA1ClientFactory(), checkInterval);
        }

        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }

    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void reset() {
        rics.clear();
        policies.clear();
        policyTypes.clear();
        services.clear();
        a1ClientFactory.reset();
    }

    @AfterEach
    void verifyNoRicLocks() {
        for (Ric ric : this.rics.getRics()) {
            ric.getLock().lockBlocking(LockType.EXCLUSIVE);
            ric.getLock().unlockBlocking();
            assertThat(ric.getLock().getLockCounter()).isZero();
            assertThat(ric.getState()).isEqualTo(Ric.RicState.AVAILABLE);
        }
    }

    @Test
    void testGetRics() throws Exception {
        addRic("ric1");
        this.addPolicyType("type1", "ric1");
        String url = "/rics?policytype_id=type1";
        String rsp = restClient().get(url).block();
        assertThat(rsp).contains("ric1");

        // nameless type for ORAN A1 1.1
        addRic("ric2");
        this.addPolicyType("", "ric2");
        url = "/rics?policytype_id=";

        // This tests also validation of trusted certs restClient(true)
        rsp = restClient(true).get(url).block();
        assertThat(rsp).contains("ric2") //
                .doesNotContain("ric1") //
                .contains("AVAILABLE");

        // All RICs
        rsp = restClient().get("/rics").block();
        assertThat(rsp).contains("ric2") //
                .contains("ric1");

        // Non existing policy type
        url = "/rics?policytype_id=XXXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testSynchronization() throws Exception {
        // Two polictypes will be put in the NearRT RICs
        PolicyTypes nearRtRicPolicyTypes = new PolicyTypes();
        nearRtRicPolicyTypes.put(createPolicyType("typeName"));
        nearRtRicPolicyTypes.put(createPolicyType("typeName2"));
        this.a1ClientFactory.setPolicyTypes(nearRtRicPolicyTypes);

        // One type and one instance added to the Policy Management Service's storage
        final String ric1Name = "ric1";
        Ric ric1 = addRic(ric1Name);
        Policy policy2 = addPolicy("policyId2", "typeName", "service", ric1Name);
        Ric ric2 = addRic("ric2");

        getA1Client(ric1Name).putPolicy(policy2); // put it in the RIC (Near-RT RIC)
        policies.remove(policy2); // Remove it from the repo -> should be deleted in the RIC

        String policyId = "policyId";
        Policy policy = addPolicy(policyId, "typeName", "service", ric1Name); // This should be created in the RIC
        supervision.checkAllRics(); // The created policy should be put in the RIC

        // Wait until synch is completed
        waitForRicState(ric1Name, RicState.SYNCHRONIZING);
        waitForRicState(ric1Name, RicState.AVAILABLE);
        waitForRicState("ric2", RicState.AVAILABLE);

        Policies ricPolicies = getA1Client(ric1Name).getPolicies();
        assertThat(ricPolicies.size()).isEqualTo(1);
        Policy ricPolicy = ricPolicies.get(policyId);
        assertThat(ricPolicy.json()).isEqualTo(policy.json());

        // Both types should be in the Policy Management Service's storage after the
        // synch
        assertThat(ric1.getSupportedPolicyTypes()).hasSize(2);
        assertThat(ric2.getSupportedPolicyTypes()).hasSize(2);
    }

    @Test
    void testGetRic() throws Exception {
        String ricId = "ric1";
        String managedElementId = "kista_1";
        addRic(ricId, managedElementId);

        String url = "/ric?managed_element_id=" + managedElementId;
        String rsp = restClient().get(url).block();
        RicInfo ricInfo = gson.fromJson(rsp, RicInfo.class);
        assertThat(ricInfo.ricId).isEqualTo(ricId);

        url = "/ric?ric_id=" + ricId;
        rsp = restClient().get(url).block();
        ricInfo = gson.fromJson(rsp, RicInfo.class);
        assertThat(ricInfo.ricId).isEqualTo(ricId);

        // test GET RIC for ManagedElement that does not exist
        url = "/ric?managed_element_id=" + "junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        url = "/ric";
        testErrorCode(restClient().get(url), HttpStatus.BAD_REQUEST);
    }

    private String putPolicyUrl(String serviceName, String ricId, String policyTypeName, String policyInstanceId,
            boolean isTransient) {
        String url;
        if (policyTypeName.isEmpty()) {
            url = "/policy?policy_id=" + policyInstanceId + "&ric_id=" + ricId + "&service_id=" + serviceName;
        } else {
            url = "/policy?policy_id=" + policyInstanceId + "&ric_id=" + ricId + "&service_id=" + serviceName
                    + "&policytype_id=" + policyTypeName;
        }
        if (isTransient) {
            url += "&transient=true";
        }
        return url;
    }

    private String putPolicyUrl(String serviceName, String ricId, String policyTypeName, String policyInstanceId) {
        return putPolicyUrl(serviceName, ricId, policyTypeName, policyInstanceId, false);
    }

    @Test
    void testPutPolicy() throws Exception {
        String serviceName = "service1";
        String ricId = "ric1";
        String policyTypeName = "type1";
        String policyInstanceId = "instance1";

        putService(serviceName);
        addPolicyType(policyTypeName, ricId);

        // PUT a transient policy
        String url = putPolicyUrl(serviceName, ricId, policyTypeName, policyInstanceId, true);
        final String policyBody = jsonString();
        this.rics.getRic(ricId).setState(Ric.RicState.AVAILABLE);

        restClient().put(url, policyBody).block();

        Policy policy = policies.getPolicy(policyInstanceId);
        assertThat(policy).isNotNull();
        assertThat(policy.id()).isEqualTo(policyInstanceId);
        assertThat(policy.ownerServiceId()).isEqualTo(serviceName);
        assertThat(policy.ric().id()).isEqualTo("ric1");
        assertThat(policy.isTransient()).isTrue();

        // Put a non transient policy
        url = putPolicyUrl(serviceName, ricId, policyTypeName, policyInstanceId);
        restClient().put(url, policyBody).block();
        policy = policies.getPolicy(policyInstanceId);
        assertThat(policy.isTransient()).isFalse();

        url = "/policies";
        String rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains policy instance ID.").contains(policyInstanceId);

        url = "/policy?policy_id=" + policyInstanceId;
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(policyBody);

        // Test of error codes
        url = putPolicyUrl(serviceName, ricId + "XX", policyTypeName, policyInstanceId);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        url = putPolicyUrl(serviceName, ricId, policyTypeName + "XX", policyInstanceId);
        addPolicyType(policyTypeName + "XX", "otherRic");
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        url = putPolicyUrl(serviceName, ricId, policyTypeName, policyInstanceId);
        this.rics.getRic(ricId).setState(Ric.RicState.SYNCHRONIZING);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.LOCKED);
        this.rics.getRic(ricId).setState(Ric.RicState.AVAILABLE);
    }

    @Test
    /**
     * Test that HttpStatus and body from failing REST call to A1 is passed on to
     * the caller.
     *
     * @throws ServiceException
     */
    void testErrorFromRic() throws ServiceException {
        putService("service1");
        addPolicyType("type1", "ric1");

        String url = putPolicyUrl("service1", "ric1", "type1", "id1");
        MockA1Client a1Client = a1ClientFactory.getOrCreateA1Client("ric1");
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String responseBody = "Refused";
        byte[] responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);

        WebClientResponseException a1Exception = new WebClientResponseException(httpStatus.value(), "statusText", null,
                responseBodyBytes, StandardCharsets.UTF_8, null);
        doReturn(Mono.error(a1Exception)).when(a1Client).putPolicy(any());

        // PUT Policy
        testErrorCode(restClient().put(url, "{}"), httpStatus, responseBody);

        // DELETE POLICY
        this.addPolicy("instance1", "type1", "service1", "ric1");
        doReturn(Mono.error(a1Exception)).when(a1Client).deletePolicy(any());
        testErrorCode(restClient().delete("/policy?policy_id=instance1"), httpStatus, responseBody);

        // GET STATUS
        this.addPolicy("instance1", "type1", "service1", "ric1");
        doReturn(Mono.error(a1Exception)).when(a1Client).getPolicyStatus(any());
        testErrorCode(restClient().get("/policy-status?policy_id=instance1"), httpStatus, responseBody);

        // Check that empty response body is OK
        a1Exception = new WebClientResponseException(httpStatus.value(), "", null, null, null, null);
        doReturn(Mono.error(a1Exception)).when(a1Client).getPolicyStatus(any());
        testErrorCode(restClient().get("/policy-status?policy_id=instance1"), httpStatus);
    }

    @Test
    void testPutTypelessPolicy() throws Exception {
        putService("service1");
        addPolicyType("", "ric1");
        String url = putPolicyUrl("service1", "ric1", "", "id1");
        restClient().put(url, jsonString()).block();

        String rsp = restClient().get("/policies").block();
        PolicyInfoList info = gson.fromJson(rsp, PolicyInfoList.class);
        assertThat(info.policies).hasSize(1);
        PolicyInfo policyInfo = info.policies.iterator().next();
        assertThat(policyInfo.policyId).isEqualTo("id1");
        assertThat(policyInfo.policyTypeId).isEmpty();
    }

    @Test
    void testRefuseToUpdatePolicy() throws Exception {
        // Test that only the json can be changed for a already created policy
        // In this case service is attempted to be changed
        this.addRic("ric1");
        this.addRic("ricXXX");
        this.addPolicy("instance1", "type1", "service1", "ric1");
        this.addPolicy("instance2", "type1", "service1", "ricXXX");

        // Try change ric1 -> ricXXX
        String urlWrongRic = putPolicyUrl("service1", "ricXXX", "type1", "instance1");
        testErrorCode(restClient().put(urlWrongRic, jsonString()), HttpStatus.CONFLICT);
    }

    @Test
    void testGetPolicy() throws Exception {
        String url = "/policy?policy_id=id";
        Policy policy = addPolicy("id", "typeName", "service1", "ric1");
        {
            String rsp = restClient().get(url).block();
            assertThat(rsp).isEqualTo(policy.json());
        }
        {
            policies.remove(policy);
            testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void testDeletePolicy() throws Exception {
        addPolicy("id", "typeName", "service1", "ric1");
        assertThat(policies.size()).isEqualTo(1);

        String url = "/policy?policy_id=id";
        ResponseEntity<String> entity = restClient().deleteForEntity(url).block();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(policies.size()).isZero();

        // Delete a non existing policy
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicySchemas() throws Exception {
        addPolicyType("type1", "ric1");
        addPolicyType("type2", "ric2");

        waitForRicState("ric1", RicState.AVAILABLE);
        waitForRicState("ric2", RicState.AVAILABLE);

        String url = "/policy-schemas";
        String rsp = this.restClient().get(url).block();
        assertThat(rsp).contains("type1") //
                .contains("{\"title\":\"type2\"}");

        PolicySchemaList info = parseSchemas(rsp);
        assertThat(info.schemas).hasSize(2);

        url = "/policy-schemas?ric_id=ric1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains("type1");
        info = parseSchemas(rsp);
        assertThat(info.schemas).hasSize(1);

        // Schema for type
        url = "/policy-schemas?policytype_id=type1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains("type1") //
                .contains("title");

        // Both type and ric specified
        url = "/policy-schemas?ric_id=ric1&policytype_id=type1";
        rsp = restClient().get(url).block();
        PolicySchemaList list = gson.fromJson(rsp, PolicySchemaList.class);
        assertThat(list.schemas).hasSize(1);

        url = "/policy-schemas?ric_id=ric1&policytype_id=type2";
        rsp = restClient().get(url).block();
        list = gson.fromJson(rsp, PolicySchemaList.class);
        assertThat(list.schemas).isEmpty();

        // Get schema for non existing RIC
        url = "/policy-schemas?ric_id=ric1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Get non existing schema
        url = "/policy-schemas?policytype_id=type1XX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    String createPolicyTypesJson(String... types) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, types);
        PolicyTypeIdList ids = new PolicyTypeIdList(list);
        return gson.toJson(ids);
    }

    @Test
    void testGetPolicyTypes() throws Exception {
        addPolicyType("type1", "ric1");
        addPolicyType("type2", "ric2");

        String url = "/policy-types";
        String rsp = restClient().get(url).block();
        String expResp = createPolicyTypesJson("type2", "type1");
        assertThat(rsp).isEqualTo(expResp);

        url = "/policy-types?ric_id=ric1";
        rsp = restClient().get(url).block();
        expResp = createPolicyTypesJson("type1");
        assertThat(rsp).isEqualTo(expResp);

        // Get policy types for non existing RIC
        url = "/policy-types?ric_id=ric1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicies() throws Exception {
        addPolicy("id1", "type1", "service1");

        String url = "/policies";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        PolicyInfoList info = gson.fromJson(rsp, PolicyInfoList.class);
        assertThat(info.policies).hasSize(1);
        PolicyInfo policyInfo = info.policies.iterator().next();
        assert (policyInfo.validate());
        assertThat(policyInfo.policyId).isEqualTo("id1");
        assertThat(policyInfo.policyTypeId).isEqualTo("type1");
        assertThat(policyInfo.serviceId).isEqualTo("service1");
    }

    @Test
    void testGetPoliciesFilter() throws Exception {
        addPolicy("id1", "type1", "service1");
        addPolicy("id2", "type1", "service2");
        addPolicy("id3", "type2", "service1");

        String url = "/policies?policytype_id=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        url = "/policies?policytype_id=type1&service_id=service2";
        rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).doesNotContain("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        // Test get policies for non existing type
        url = "/policies?policytype_id=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policies for non existing RIC
        url = "/policies?ric_id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicyIdsFilter() throws Exception {
        addPolicy("id1", "type1", "service1", "ric1");
        addPolicy("id2", "type1", "service2", "ric1");
        addPolicy("id3", "type2", "service1", "ric1");

        String url = "/policy-ids?policytype_id=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        url = "/policy-ids?policytype_id=type1&service_id=service1&ric=ric1";
        rsp = restClient().get(url).block();
        PolicyIdList respList = gson.fromJson(rsp, PolicyIdList.class);
        assertThat(respList.policyIds.iterator().next()).isEqualTo("id1");

        // Test get policy ids for non existing type
        url = "/policy-ids?policytype_id=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policy ids for non existing RIC
        url = "/policy-ids?ric_id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testPutAndGetService() throws Exception {
        // PUT
        String serviceName = "name";
        putService(serviceName, 0, HttpStatus.CREATED);
        putService(serviceName, 0, HttpStatus.OK);

        // GET one service
        String url = "/services?service_id=name";
        String rsp = restClient().get(url).block();
        ServiceStatusList info = gson.fromJson(rsp, ServiceStatusList.class);
        assertThat(info.statusList).hasSize(1);
        ServiceStatus status = info.statusList.iterator().next();
        assertThat(status.keepAliveIntervalSeconds).isZero();
        assertThat(status.serviceId).isEqualTo(serviceName);

        // GET (all)
        url = "/services";
        rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains service name").contains(serviceName);
        logger.info(rsp);

        // Keep alive
        url = "/services/keepalive?service_id=name";
        ResponseEntity<?> entity = restClient().putForEntity(url).block();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE service
        assertThat(services.size()).isEqualTo(1);
        url = "/services?service_id=name";
        restClient().delete(url).block();
        assertThat(services.size()).isZero();

        // Keep alive, no registered service
        testErrorCode(restClient().put("/services/keepalive?service_id=name", ""), HttpStatus.NOT_FOUND);

        // PUT service with bad payload
        testErrorCode(restClient().put("/services", "crap"), HttpStatus.BAD_REQUEST, false);
        testErrorCode(restClient().put("/services", "{}"), HttpStatus.BAD_REQUEST, false);
        testErrorCode(restClient().put("/services", createServiceJson(serviceName, -123)), HttpStatus.BAD_REQUEST,
                false);
        testErrorCode(restClient().put("/services", createServiceJson(serviceName, 0, "missing.portandprotocol.com")),
                HttpStatus.BAD_REQUEST, false);

        // GET non existing service
        testErrorCode(restClient().get("/services?service_id=XXX"), HttpStatus.NOT_FOUND);
    }

    @Test
    void testServiceSupervision() throws Exception {
        putService("service1", 1, HttpStatus.CREATED);
        addPolicyType("type1", "ric1");

        String url = putPolicyUrl("service1", "ric1", "type1", "instance1");
        final String policyBody = jsonString();
        restClient().put(url, policyBody).block();

        assertThat(policies.size()).isEqualTo(1);
        assertThat(services.size()).isEqualTo(1);

        // Timeout after ~1 second
        await().untilAsserted(() -> assertThat(policies.size()).isZero());
        assertThat(services.size()).isZero();
    }

    @Test
    void testGetPolicyStatus() throws Exception {
        addPolicy("id", "typeName", "service1", "ric1");
        assertThat(policies.size()).isEqualTo(1);

        String url = "/policy-status?policy_id=id";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("OK");

        // GET non existing policy status
        url = "/policy-status?policy_id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    private Policy addPolicy(String id, String typeName, String service, String ric) throws ServiceException {
        addRic(ric);
        Policy policy = ImmutablePolicy.builder() //
                .id(id) //
                .json(jsonString()) //
                .ownerServiceId(service) //
                .ric(rics.getRic(ric)) //
                .type(addPolicyType(typeName, ric)) //
                .lastModified(Instant.now()) //
                .isTransient(false) //
                .build();
        policies.put(policy);
        return policy;
    }

    private Policy addPolicy(String id, String typeName, String service) throws ServiceException {
        return addPolicy(id, typeName, service, "ric");
    }

    private String createServiceJson(String name, long keepAliveIntervalSeconds) {
        return createServiceJson(name, keepAliveIntervalSeconds, "https://examples.javacodegeeks.com/core-java/");
    }

    private String createServiceJson(String name, long keepAliveIntervalSeconds, String url) {
        ServiceRegistrationInfo service = new ServiceRegistrationInfo(name, keepAliveIntervalSeconds, url);

        String json = gson.toJson(service);
        return json;
    }

    private void putService(String name) {
        putService(name, 0, null);
    }

    private void putService(String name, long keepAliveIntervalSeconds, @Nullable HttpStatus expectedStatus) {
        String url = "/services";
        String body = createServiceJson(name, keepAliveIntervalSeconds);
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        if (expectedStatus != null) {
            assertEquals(expectedStatus, resp.getStatusCode(), "");
        }
    }

    private String baseUrl() {
        return "https://localhost:" + port + Consts.V2_API_ROOT;
    }

    private String jsonString() {
        return "{\"servingCellNrcgi\":\"1\"}";
    }

    @Test
    void testConcurrency() throws Exception {
        final Instant startTime = Instant.now();
        List<Thread> threads = new ArrayList<>();
        List<ConcurrencyTestRunnable> tests = new ArrayList<>();
        a1ClientFactory.setResponseDelay(Duration.ofMillis(1));
        addRic("ric");
        addPolicyType("type1", "ric");
        addPolicyType("type2", "ric");

        for (int i = 0; i < 10; ++i) {
            AsyncRestClient restClient = restClient();
            ConcurrencyTestRunnable test =
                    new ConcurrencyTestRunnable(restClient, supervision, a1ClientFactory, rics, policyTypes);
            Thread thread = new Thread(test, "TestThread_" + i);
            thread.start();
            threads.add(thread);
            tests.add(test);
        }
        for (Thread t : threads) {
            t.join();
        }
        for (ConcurrencyTestRunnable test : tests) {
            assertThat(test.isFailed()).isFalse();
        }
        assertThat(policies.size()).isZero();
        logger.info("Concurrency test took " + Duration.between(startTime, Instant.now()));
    }

    private AsyncRestClient restClient(boolean useTrustValidation) {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        config = ImmutableWebClientConfig.builder() //
                .keyStoreType(config.keyStoreType()) //
                .keyStorePassword(config.keyStorePassword()) //
                .keyStore(config.keyStore()) //
                .keyPassword(config.keyPassword()) //
                .isTrustStoreUsed(useTrustValidation) //
                .trustStore(config.trustStore()) //
                .trustStorePassword(config.trustStorePassword()) //
                .build();

        return new AsyncRestClient(baseUrl(), config);
    }

    private AsyncRestClient restClient() {
        return restClient(false);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus) {
        testErrorCode(request, expStatus, "", true);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, boolean expectApplicationProblemJsonMediaType) {
        testErrorCode(request, expStatus, "", expectApplicationProblemJsonMediaType);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        testErrorCode(request, expStatus, responseContains, true);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains,
            boolean expectApplicationProblemJsonMediaType) {
        StepVerifier.create(request) //
                .expectSubscription() //
                .expectErrorMatches(
                        t -> checkWebClientError(t, expStatus, responseContains, expectApplicationProblemJsonMediaType)) //
                .verify();
    }

    private void waitForRicState(String ricId, RicState state) throws ServiceException {
        Ric ric = rics.getRic(ricId);
        await().untilAsserted(() -> state.equals(ric.getState()));
    }

    private boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains,
            boolean expectApplicationProblemJsonMediaType) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);
        assertThat(responseException.getResponseBodyAsString()).contains(responseContains);
        if (expectApplicationProblemJsonMediaType) {
            assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return true;
    }

    private MockA1Client getA1Client(String ricId) throws ServiceException {
        return a1ClientFactory.getOrCreateA1Client(ricId);
    }

    private PolicyType createPolicyType(String policyTypeName) {
        return ImmutablePolicyType.builder() //
                .id(policyTypeName) //
                .schema("{\"title\":\"" + policyTypeName + "\"}") //
                .build();
    }

    private PolicyType addPolicyType(String policyTypeName, String ricId) {
        PolicyType type = createPolicyType(policyTypeName);
        policyTypes.put(type);
        addRic(ricId).addSupportedPolicyType(type);
        return type;
    }

    private Ric addRic(String ricId) {
        return addRic(ricId, null);
    }

    private Ric addRic(String ricId, String managedElement) {
        if (rics.get(ricId) != null) {
            return rics.get(ricId);
        }
        List<String> mes = new ArrayList<>();
        if (managedElement != null) {
            mes.add(managedElement);
        }
        RicConfig conf = ImmutableRicConfig.builder() //
                .ricId(ricId) //
                .baseUrl(ricId) //
                .managedElementIds(mes) //
                .controllerName("") //
                .build();
        Ric ric = new Ric(conf);
        ric.setState(Ric.RicState.AVAILABLE);
        this.rics.put(ric);
        return ric;
    }

    private static PolicySchemaList parseSchemas(String jsonString) {
        return gson.fromJson(jsonString, PolicySchemaList.class);
    }
}
