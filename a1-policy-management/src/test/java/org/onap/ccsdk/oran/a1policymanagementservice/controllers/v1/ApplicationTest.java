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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableRicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ImmutableWebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
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
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.vardata-directory=./target"})
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

    private static Gson gson = new GsonBuilder().create();

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

        @Bean
        public ApplicationConfig getApplicationConfig() {
            return new MockApplicationConfig();
        }

        @Bean
        MockA1ClientFactory getA1ClientFactory(@Autowired ApplicationConfig appConfig, @Autowired PolicyTypes types) {
            return new MockA1ClientFactory(appConfig, types);
        }

        @Bean
        public ServiceSupervision getServiceSupervision(@Autowired Services services,
                @Autowired A1ClientFactory a1ClientFactory, @Autowired Policies policies) {
            Duration checkInterval = Duration.ofMillis(1);
            return new ServiceSupervision(services, policies, a1ClientFactory, checkInterval);
        }

        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }

    }

    @LocalServerPort
    private int port;

    @AfterEach
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
        String url = "/rics?policyType=type1";
        String rsp = restClient().get(url).block();
        assertThat(rsp).contains("ric1");

        // nameless type for ORAN A1 1.1
        addRic("ric2");
        this.addPolicyType("", "ric2");
        url = "/rics?policyType=";

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
        url = "/rics?policyType=XXXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testSynchronization() throws Exception {
        // Two polictypes will be put in the NearRT RICs
        PolicyTypes nearRtRicPolicyTypes = new PolicyTypes(this.applicationConfig);
        nearRtRicPolicyTypes.put(createPolicyType("typeName"));
        nearRtRicPolicyTypes.put(createPolicyType("typeName2"));
        this.a1ClientFactory.setPolicyTypes(nearRtRicPolicyTypes);

        // One type and one instance added to the Policy Management Service's storage
        final String ric1Name = "ric1";
        Ric ric1 = addRic(ric1Name);
        Policy policy2 = addPolicy("policyId2", "typeName", "service", ric1Name);
        Ric ric2 = addRic("ric2");

        getA1Client(ric1Name).putPolicy(policy2); // put it in the RIC
        policies.remove(policy2); // Remove it from the repo -> should be deleted in the RIC

        String policyId = "policyId";
        Policy policy = addPolicy(policyId, "typeName", "service", ric1Name); // This should be created in the RIC
        supervision.checkAllRics(); // The created policy should be put in the RIC

        // Wait until synch is completed
        await().untilAsserted(() -> RicState.SYNCHRONIZING.equals(rics.getRic(ric1Name).getState()));
        await().untilAsserted(() -> RicState.AVAILABLE.equals(rics.getRic(ric1Name).getState()));
        await().untilAsserted(() -> RicState.AVAILABLE.equals(rics.getRic("ric2").getState()));

        Policies ricPolicies = getA1Client(ric1Name).getPolicies();
        assertThat(ricPolicies.size()).isEqualTo(1);
        Policy ricPolicy = ricPolicies.get(policyId);
        assertThat(ricPolicy.getJson()).isEqualTo(policy.getJson());

        // Both types should be in the Policy Management Service's storage after the
        // synch
        assertThat(ric1.getSupportedPolicyTypes()).hasSize(2);
        assertThat(ric2.getSupportedPolicyTypes()).hasSize(2);
    }

    @Test
    void testGetRicForManagedElement_thenReturnCorrectRic() throws Exception {
        String ricName = "ric1";
        String managedElementId = "kista_1";
        addRic(ricName, managedElementId);

        String url = "/ric?managedElementId=" + managedElementId;
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(ricName);

        // test GET RIC for ManagedElement that does not exist
        url = "/ric?managedElementId=" + "junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    private String putPolicyUrl(String serviceName, String ricName, String policyTypeName, String policyInstanceId,
            boolean isTransient) {
        String url;
        if (policyTypeName.isEmpty()) {
            url = "/policy?id=" + policyInstanceId + "&ric=" + ricName + "&service=" + serviceName;
        } else {
            url = "/policy?id=" + policyInstanceId + "&ric=" + ricName + "&service=" + serviceName + "&type="
                    + policyTypeName;
        }
        if (isTransient) {
            url += "&transient=true";
        }
        return url;
    }

    private String putPolicyUrl(String serviceName, String ricName, String policyTypeName, String policyInstanceId) {
        return putPolicyUrl(serviceName, ricName, policyTypeName, policyInstanceId, false);
    }

    @Test
    void testPutPolicy() throws Exception {
        String serviceName = "service1";
        String ricName = "ric1";
        String policyTypeName = "type1";
        String policyInstanceId = "instance1";

        putService(serviceName);
        addPolicyType(policyTypeName, ricName);

        // PUT a transient policy
        String url = putPolicyUrl(serviceName, ricName, policyTypeName, policyInstanceId, true);
        final String policyBody = jsonString();
        this.rics.getRic(ricName).setState(Ric.RicState.AVAILABLE);

        restClient().put(url, policyBody).block();

        Policy policy = policies.getPolicy(policyInstanceId);
        assertThat(policy).isNotNull();
        assertThat(policy.getId()).isEqualTo(policyInstanceId);
        assertThat(policy.getOwnerServiceId()).isEqualTo(serviceName);
        assertThat(policy.getRic().id()).isEqualTo("ric1");
        assertThat(policy.isTransient()).isTrue();

        // Put a non transient policy
        url = putPolicyUrl(serviceName, ricName, policyTypeName, policyInstanceId);
        restClient().put(url, policyBody).block();
        policy = policies.getPolicy(policyInstanceId);
        assertThat(policy.isTransient()).isFalse();

        url = "/policies";
        String rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains policy instance ID.").contains(policyInstanceId);

        url = "/policy?id=" + policyInstanceId;
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(policyBody);

        // Test of error codes
        url = putPolicyUrl(serviceName, ricName + "XX", policyTypeName, policyInstanceId);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        url = putPolicyUrl(serviceName, ricName, policyTypeName + "XX", policyInstanceId);
        addPolicyType(policyTypeName + "XX", "otherRic");
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        url = putPolicyUrl(serviceName, ricName, policyTypeName, policyInstanceId);
        this.rics.getRic(ricName).setState(Ric.RicState.SYNCHRONIZING);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.LOCKED);
        this.rics.getRic(ricName).setState(Ric.RicState.AVAILABLE);
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
        testErrorCode(restClient().delete("/policy?id=instance1"), httpStatus, responseBody);

        // GET STATUS
        this.addPolicy("instance1", "type1", "service1", "ric1");
        doReturn(Mono.error(a1Exception)).when(a1Client).getPolicyStatus(any());
        testErrorCode(restClient().get("/policy_status?id=instance1"), httpStatus, responseBody);

        // Check that empty response body is OK
        a1Exception = new WebClientResponseException(httpStatus.value(), "", null, null, null, null);
        doReturn(Mono.error(a1Exception)).when(a1Client).getPolicyStatus(any());
        testErrorCode(restClient().get("/policy_status?id=instance1"), httpStatus);
    }

    @Test
    void testPutTypelessPolicy() throws Exception {
        putService("service1");
        addPolicyType("", "ric1");
        String url = putPolicyUrl("service1", "ric1", "", "id1");
        restClient().put(url, jsonString()).block();

        String rsp = restClient().get("/policies").block();
        List<PolicyInfo> info = parseList(rsp, PolicyInfo.class);
        assertThat(info).hasSize(1);
        PolicyInfo policyInfo = info.get(0);
        assertThat(policyInfo.id).isEqualTo("id1");
        assertThat(policyInfo.type).isEmpty();
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
        String url = "/policy?id=id";
        Policy policy = addPolicy("id", "typeName", "service1", "ric1");
        {
            String rsp = restClient().get(url).block();
            assertThat(rsp).isEqualTo(policy.getJson());
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

        String url = "/policy?id=id";
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

        String url = "/policy_schemas";
        String rsp = this.restClient().get(url).block();
        assertThat(rsp).contains("type1") //
                .contains("{\"title\":\"type2\"}");

        List<String> info = parseSchemas(rsp);
        assertThat(info).hasSize(2);

        url = "/policy_schemas?ric=ric1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains("type1");
        info = parseSchemas(rsp);
        assertThat(info).hasSize(1);

        // Get schema for non existing RIC
        url = "/policy_schemas?ric=ric1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicySchema() throws Exception {
        addPolicyType("type1", "ric1");
        addPolicyType("type2", "ric2");

        String url = "/policy_schema?id=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("type1") //
                .contains("title");

        // Get non existing schema
        url = "/policy_schema?id=type1XX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicyTypes() throws Exception {
        addPolicyType("type1", "ric1");
        addPolicyType("type2", "ric2");

        String url = "/policy_types";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[\"type2\",\"type1\"]");

        url = "/policy_types?ric=ric1";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[\"type1\"]");

        // Get policy types for non existing RIC
        url = "/policy_types?ric=ric1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicies() throws Exception {
        addPolicy("id1", "type1", "service1");

        String url = "/policies";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        List<PolicyInfo> info = parseList(rsp, PolicyInfo.class);
        assertThat(info).hasSize(1);
        PolicyInfo policyInfo = info.get(0);
        assert (policyInfo.validate());
        assertThat(policyInfo.id).isEqualTo("id1");
        assertThat(policyInfo.type).isEqualTo("type1");
        assertThat(policyInfo.service).isEqualTo("service1");
    }

    @Test
    void testGetPoliciesFilter() throws Exception {
        addPolicy("id1", "type1", "service1");
        addPolicy("id2", "type1", "service2");
        addPolicy("id3", "type2", "service1");

        String url = "/policies?type=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        url = "/policies?type=type1&service=service2";
        rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).doesNotContain("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        // Test get policies for non existing type
        url = "/policies?type=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policies for non existing RIC
        url = "/policies?ric=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetPolicyIdsFilter() throws Exception {
        addPolicy("id1", "type1", "service1", "ric1");
        addPolicy("id2", "type1", "service2", "ric1");
        addPolicy("id3", "type2", "service1", "ric1");

        String url = "/policy_ids?type=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1") //
                .contains("id2") //
                .doesNotContain("id3");

        url = "/policy_ids?type=type1&service=service1&ric=ric1";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[\"id1\"]");

        // Test get policy ids for non existing type
        url = "/policy_ids?type=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policy ids for non existing RIC
        url = "/policy_ids?ric=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    void testPutAndGetService() throws Exception {
        // PUT
        String serviceName = "name";
        putService(serviceName, 0, HttpStatus.CREATED);
        putService(serviceName, 0, HttpStatus.OK);

        // GET one service
        String url = "/services?name=name";
        String rsp = restClient().get(url).block();
        List<ServiceStatus> info = parseList(rsp, ServiceStatus.class);
        assertThat(info).hasSize(1);
        ServiceStatus status = info.iterator().next();
        assertThat(status.keepAliveIntervalSeconds).isZero();
        assertThat(status.serviceName).isEqualTo(serviceName);

        // GET (all)
        url = "/services";
        rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains service name").contains(serviceName);
        logger.info(rsp);

        // Keep alive
        url = "/services/keepalive?name=name";
        ResponseEntity<String> entity = restClient().putForEntity(url).block();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE service
        assertThat(services.size()).isEqualTo(1);
        url = "/services?name=name";
        restClient().delete(url).block();
        assertThat(services.size()).isZero();

        // Keep alive, no registered service
        testErrorCode(restClient().put("/services/keepalive?name=name", ""), HttpStatus.NOT_FOUND);

        // PUT servive with bad payload
        testErrorCode(restClient().put("/service", "crap"), HttpStatus.BAD_REQUEST);
        testErrorCode(restClient().put("/service", "{}"), HttpStatus.BAD_REQUEST);
        testErrorCode(restClient().put("/service", createServiceJson(serviceName, -123)), HttpStatus.BAD_REQUEST);
        testErrorCode(restClient().put("/service", createServiceJson(serviceName, 0, "missing.portandprotocol.com")),
                HttpStatus.BAD_REQUEST);

        // GET non existing service
        testErrorCode(restClient().get("/services?name=XXX"), HttpStatus.NOT_FOUND);
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

        String url = "/policy_status?id=id";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("OK");

        // GET non existing policy status
        url = "/policy_status?id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    private Policy addPolicy(String id, String typeName, String service, String ric) throws ServiceException {
        addRic(ric);
        Policy policy = Policy.builder() //
                .id(id) //
                .json(jsonString()) //
                .ownerServiceId(service) //
                .ric(rics.getRic(ric)) //
                .type(addPolicyType(typeName, ric)) //
                .lastModified(Instant.now()) //
                .isTransient(false) //
                .statusNotificationUri("/policy_status?id=XXX") //
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
        String url = "/service";
        String body = createServiceJson(name, keepAliveIntervalSeconds);
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        if (expectedStatus != null) {
            assertEquals(expectedStatus, resp.getStatusCode(), "");
        }
    }

    private String baseUrl() {
        return "https://localhost:" + port;
    }

    private String jsonString() {
        return "{\"servingCellNrcgi\":\"1\"}";
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
                .httpProxyConfig(config.httpProxyConfig()) //
                .build();

        AsyncRestClientFactory f = new AsyncRestClientFactory(config);
        return f.createRestClientNoHttpProxy(baseUrl());
    }

    private AsyncRestClient restClient() {
        return restClient(false);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus) {
        testErrorCode(request, expStatus, "");
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        StepVerifier.create(request) //
                .expectSubscription() //
                .expectErrorMatches(t -> checkWebClientError(t, expStatus, responseContains)) //
                .verify();
    }

    private boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);
        assertThat(responseException.getResponseBodyAsString()).contains(responseContains);
        return true;
    }

    private MockA1Client getA1Client(String ricName) throws ServiceException {
        return a1ClientFactory.getOrCreateA1Client(ricName);
    }

    private PolicyType createPolicyType(String policyTypeName) {
        return PolicyType.builder() //
                .id(policyTypeName) //
                .schema("{\"title\":\"" + policyTypeName + "\"}") //
                .build();
    }

    private PolicyType addPolicyType(String policyTypeName, String ricName) {
        PolicyType type = createPolicyType(policyTypeName);
        policyTypes.put(type);
        addRic(ricName).addSupportedPolicyType(type);
        return type;
    }

    private Ric addRic(String ricName) {
        return addRic(ricName, null);
    }

    private Ric addRic(String ricName, String managedElement) {
        if (rics.get(ricName) != null) {
            return rics.get(ricName);
        }
        List<String> mes = new ArrayList<>();
        if (managedElement != null) {
            mes.add(managedElement);
        }
        RicConfig conf = ImmutableRicConfig.builder() //
                .ricId(ricName) //
                .baseUrl(ricName) //
                .managedElementIds(mes) //
                .controllerName("") //
                .build();
        Ric ric = new Ric(conf);
        ric.setState(Ric.RicState.AVAILABLE);
        this.rics.put(ric);
        return ric;
    }

    private static <T> List<T> parseList(String jsonString, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        JsonArray jsonArr = JsonParser.parseString(jsonString).getAsJsonArray();
        for (JsonElement jsonElement : jsonArr) {
            T json = gson.fromJson(jsonElement.toString(), clazz);
            result.add(json);
        }
        return result;
    }

    private static List<String> parseSchemas(String jsonString) {
        JsonArray arrayOfSchema = JsonParser.parseString(jsonString).getAsJsonArray();
        List<String> result = new ArrayList<>();
        for (JsonElement schemaObject : arrayOfSchema) {
            result.add(schemaObject.toString());
        }
        return result;
    }
}
