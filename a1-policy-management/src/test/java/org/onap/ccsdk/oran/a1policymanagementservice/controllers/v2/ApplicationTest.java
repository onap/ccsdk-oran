/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2023 Nordix Foundation. All rights reserved.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1ClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClient;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.AsyncRestClientFactory;
import org.onap.ccsdk.oran.a1policymanagementservice.clients.SecurityContext;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.OpenPolicyAgentSimulatorController;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.ServiceCallbackInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest;
import org.onap.ccsdk.oran.a1policymanagementservice.controllers.authorization.PolicyAuthorizationRequest.Input.AccessType;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.RicInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyTypeDefinition;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyTypeIdList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyInfoList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyIdList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.PolicyStatusInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceStatusList;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceStatus;
import org.onap.ccsdk.oran.a1policymanagementservice.models.v2.ServiceRegistrationInfo;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Lock.LockType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policies;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Policy;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyType;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.PolicyTypes;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Rics;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Service;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Services;
import org.onap.ccsdk.oran.a1policymanagementservice.tasks.RefreshConfigTask;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.annotation.Nullable;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.webclient.trust-store-used=true", //
        "app.vardata-directory=/tmp/pmstest", //
        "app.filepath=", //
        "app.s3.bucket=" // If this is set, S3 will be used to store data.
})
class ApplicationTest {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
    private ObjectMapper objectMapper;

    @Autowired
    RicSupervision supervision;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    Services services;

    @Autowired
    RappSimulatorController rAppSimulator;

    @Autowired
    RefreshConfigTask refreshConfigTask;

    @Autowired
    SecurityContext securityContext;

    @Autowired
    OpenPolicyAgentSimulatorController openPolicyAgentSimulatorController;

    private static Gson gson = new GsonBuilder().create();

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory {

        @Bean
        A1ClientFactory getA1ClientFactory(@Autowired ApplicationConfig appConfig, @Autowired PolicyTypes types) {
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

    @BeforeEach
    void init() {
        this.applicationConfig.setAuthProviderUrl(baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL);
    }

    @AfterEach
    void reset() {
        rics.clear();
        policies.clear();
        policyTypes.clear();
        services.clear();
        a1ClientFactory.reset();
        this.rAppSimulator.getTestResults().clear();
        this.a1ClientFactory.setPolicyTypes(policyTypes); // Default same types in RIC and in this app
        this.securityContext.setAuthTokenFilePath(null);
        this.openPolicyAgentSimulatorController.getTestResults().reset();
    }

    @AfterAll
    static void clearTestDir() {
        try {
            FileSystemUtils.deleteRecursively(Path.of("/tmp/pmstest"));
        } catch (Exception e) {
            logger.warn("Could test directory : {}", e.getMessage());
        }
    }

    @AfterEach
    void verifyNoRicLocks() {
        for (Ric ric : this.rics.getRics()) {
            Lock.Grant grant = ric.getLock().lockBlocking(LockType.EXCLUSIVE, "verifyNoRicLocks");
            grant.unlockBlocking();
            assertThat(ric.getLock().getLockCounter()).isZero();
            assertThat(ric.getState()).isEqualTo(Ric.RicState.AVAILABLE);
        }
    }

    @Test
    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    @DisplayName("test ZZ Actuator")
    void testZZActuator() throws Exception {
        // The test must be run last, hence the "ZZ" in the name. All succeeding tests
        // will fail.
        AsyncRestClient client = restClient(baseUrl(), false);

        client.post("/actuator/loggers/org.onap.ccsdk.oran.a1policymanagementservice",
                "{\"configuredLevel\":\"trace\"}").block();

        String resp = client.get("/actuator/loggers/org.onap.ccsdk.oran.a1policymanagementservice").block();
        assertThat(resp).contains("TRACE");

        client.post("/actuator/loggers/org.springframework.boot.actuate", "{\"configuredLevel\":\"trace\"}").block();

        // This will stop the web server and all coming tests will fail.
        client.post("/actuator/shutdown", "").block();

        Thread.sleep(1000);

        StepVerifier.create(restClient().get("/rics")) // Any call
                .expectSubscription() //
                .expectErrorMatches(t -> t instanceof WebClientRequestException) //
                .verify();

    }

    @Test
    @DisplayName("test Persistency Policies")
    void testPersistencyPolicies() throws Exception {
        Ric ric = this.addRic("ric1");
        PolicyType type = this.addPolicyType("type1", ric.id());

        final int noOfPolicies = 100;
        for (int i = 0; i < noOfPolicies; ++i) {
            addPolicy("id" + i, type.getId(), "service", ric.id());
        }
        waitforS3();

        {
            Policies policies = new Policies(this.applicationConfig);
            policies.restoreFromDatabase(ric, this.policyTypes).blockLast();
            assertThat(policies.size()).isEqualTo(noOfPolicies);
        }

        {
            restClient().delete("/policies/id2").block();
            Policies policies = new Policies(this.applicationConfig);
            policies.restoreFromDatabase(ric, this.policyTypes).blockLast();
            assertThat(policies.size()).isEqualTo(noOfPolicies - 1);
        }
    }

    @Test
    @DisplayName("test Persistency Policy Types")
    void testPersistencyPolicyTypes() throws Exception {
        Ric ric = this.addRic("ric1");
        this.addPolicyType("type1", ric.id());
        waitforS3();

        PolicyTypes types = new PolicyTypes(this.applicationConfig);
        types.restoreFromDatabase().blockLast();
        assertThat(types.size()).isEqualTo(1);
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    private void waitforS3() throws Exception {
        if (applicationConfig.isS3Enabled()) {
            Thread.sleep(1000);
        }
    }

    @Test
    @DisplayName("test Persistency Service")
    void testPersistencyService() throws Exception {
        final String SERVICE = "serviceName";
        putService(SERVICE, 1234, HttpStatus.CREATED);
        assertThat(this.services.size()).isEqualTo(1);
        Service service = this.services.getService(SERVICE);
        waitforS3();

        Services servicesRestored = new Services(this.applicationConfig);
        servicesRestored.restoreFromDatabase().blockLast();
        Service serviceRestored = servicesRestored.getService(SERVICE);
        assertThat(servicesRestored.size()).isEqualTo(1);
        assertThat(serviceRestored.getCallbackUrl()).isEqualTo(service.getCallbackUrl());
        assertThat(serviceRestored.getKeepAliveInterval()).isEqualTo(service.getKeepAliveInterval());

        // check that the service can be deleted
        this.services.remove(SERVICE);
        servicesRestored = new Services(this.applicationConfig);
        assertThat(servicesRestored.size()).isZero();
    }

    @Test
    @DisplayName("test Adding Ric From Configuration")
    void testAddingRicFromConfiguration() throws Exception {
        // Test adding the RIC from configuration

        final String RIC = "ric1";
        final String TYPE = "type123";
        PolicyTypes nearRtRicPolicyTypes = new PolicyTypes(this.applicationConfig);
        nearRtRicPolicyTypes.put(createPolicyType(TYPE));
        this.a1ClientFactory.setPolicyTypes(nearRtRicPolicyTypes);

        putService("service");

        refreshConfigTask.handleUpdatedRicConfig( //
                new RicConfigUpdate(ricConfig(RIC, "me1"), RicConfigUpdate.Type.ADDED)) //
                .block();
        waitForRicState(RIC, RicState.AVAILABLE);

        // Test that the type has been synched
        Ric addedRic = this.rics.getRic(RIC);
        assertThat(addedRic.getSupportedPolicyTypes()).hasSize(1);
        assertThat(addedRic.getSupportedPolicyTypes().iterator().next().getId()).isEqualTo(TYPE);

        // Check that a service callback for the AVAILABLE RIC is invoked
        final RappSimulatorController.TestResults receivedCallbacks = rAppSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(receivedCallbacks.getReceivedInfo()).hasSize(1));
        ServiceCallbackInfo callbackInfo = receivedCallbacks.getReceivedInfo().get(0);
        assertThat(callbackInfo.ricId).isEqualTo(RIC);
        assertThat(callbackInfo.eventType).isEqualTo(ServiceCallbackInfo.EventType.AVAILABLE);
    }

    @Test
    @DisplayName("test Adding Ric From Configuration non Responding Ric")
    void testAddingRicFromConfiguration_nonRespondingRic() throws Exception {
        putService("service");

        final String RIC = "NonRespondingRic";
        MockA1Client a1Client = a1ClientFactory.getOrCreateA1Client(RIC);
        doReturn(MockA1Client.monoError("error", HttpStatus.BAD_GATEWAY)).when(a1Client).getPolicyTypeIdentities();

        refreshConfigTask.handleUpdatedRicConfig( //
                new RicConfigUpdate(ricConfig(RIC, "me1"), RicConfigUpdate.Type.ADDED)) //
                .block();

        waitForRicState(RIC, RicState.UNAVAILABLE);

        // Check that no service callback for the UNAVAILABLE RIC is invoked
        final RappSimulatorController.TestResults receivedCallbacks = rAppSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(receivedCallbacks.getReceivedInfo()).isEmpty());

        // Run a synch and check that the AVAILABLE notification is received
        a1ClientFactory.reset();
        supervision.checkAllRics();
        waitForRicState(RIC, RicState.AVAILABLE);

        await().untilAsserted(() -> assertThat(receivedCallbacks.getReceivedInfo()).hasSize(1));
    }

    @Test
    @DisplayName("test Trust Validation")
    void testTrustValidation() {
        addRic("ric1");

        String rsp = restClient(true).get("/rics").block(); // restClient(true) enables trust validation
        assertThat(rsp).contains("ric1");

    }

    @Test
    @DisplayName("test Get Rics")
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
        rsp = restClient().get(url).block();
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
    @DisplayName("test Synchronization")
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
        assertThat(ricPolicy.getJson()).isEqualTo(policy.getJson());

        // Both types should be in the Policy Management Service's storage after the
        // synch
        assertThat(ric1.getSupportedPolicyTypes()).hasSize(2);
        assertThat(ric2.getSupportedPolicyTypes()).hasSize(2);
    }

    @Test
    @DisplayName("test Get Ric")
    void testGetRic() throws Exception {
        String ricId = "ric1";
        String managedElementId = "kista_1";
        addRic(ricId, managedElementId);

        String url = "/rics/ric?managed_element_id=" + managedElementId;
        String rsp = restClient().get(url).block();
        RicInfo ricInfo = objectMapper.readValue(rsp, RicInfo.class);
        assertThat(ricInfo.getRicId()).isEqualTo(ricId);

        url = "/rics/ric?ric_id=" + ricId;
        rsp = restClient().get(url).block();
        ricInfo = objectMapper.readValue(rsp, RicInfo.class);
        assertThat(ricInfo.getRicId()).isEqualTo(ricId);

        // test GET RIC for ManagedElement that does not exist
        url = "/rics/ric?managed_element_id=" + "junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        url = "/rics/ric";
        testErrorCode(restClient().get(url), HttpStatus.BAD_REQUEST);
    }

    private String putPolicyBody(String serviceName, String ricId, String policyTypeName, String policyInstanceId,
            boolean isTransient, String statusNotificationUri) throws JsonProcessingException {
        PolicyInfo policyInfo = new PolicyInfo();
        policyInfo.setPolicyId(policyInstanceId);
        policyInfo.setPolicytypeId(policyTypeName);
        policyInfo.setRicId(ricId);
        policyInfo.setServiceId(serviceName);
        policyInfo.setPolicyData(jsonString());
        policyInfo.setIsTransient(isTransient);
        policyInfo.setStatusNotificationUri(statusNotificationUri);
        return objectMapper.writeValueAsString(policyInfo);
    }

    private String putPolicyBod(String serviceName, String ricId, String policyTypeName, String policyInstanceId,
                                       boolean isTransient, String statusNotificationUri) throws JsonProcessingException {
        PolicyInfo policyInfo = new PolicyInfo();
        policyInfo.setPolicyId(policyInstanceId);
        policyInfo.setPolicytypeId(policyTypeName);
        policyInfo.setRicId(ricId);
        policyInfo.setServiceId(serviceName);
        policyInfo.setPolicyData(jsonString());
        policyInfo.setIsTransient(isTransient);
        policyInfo.setStatusNotificationUri(statusNotificationUri);
        return objectMapper.writeValueAsString(policyInfo);
    }

    private String putPolicyBody(String serviceName, String ricId, String policyTypeName, String policyInstanceId) throws JsonProcessingException {
        return putPolicyBody(serviceName, ricId, policyTypeName, policyInstanceId, false, "statusUri");
    }

    @Test
    @DisplayName("test Put Policy")
    void testPutPolicy() throws Exception {
        String serviceName = "service.1";
        String ricId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String policyInstanceId = "instance_1.2.3";

        putService(serviceName);
        addPolicyType(policyTypeName, ricId);

        // PUT a transient policy
        String url = "/policies";
        String policyBody = putPolicyBody(serviceName, ricId, policyTypeName, policyInstanceId, true, "statusNotif");
        this.rics.getRic(ricId).setState(Ric.RicState.AVAILABLE);

        restClient().put(url, policyBody).block();
        {
            // Check the authorization request
            OpenPolicyAgentSimulatorController.TestResults res =
                    this.openPolicyAgentSimulatorController.getTestResults();
            assertThat(res.receivedRequests).hasSize(1);
            PolicyAuthorizationRequest req = res.receivedRequests.get(0);
            assertThat(req.getInput().getAccessType()).isEqualTo(AccessType.WRITE);
            assertThat(req.getInput().getPolicyTypeId()).isEqualTo(policyTypeName);
        }

        Policy policy = policies.getPolicy(policyInstanceId);
        assertThat(policy).isNotNull();
        assertThat(policy.getId()).isEqualTo(policyInstanceId);
        assertThat(policy.getOwnerServiceId()).isEqualTo(serviceName);
        assertThat(policy.getRic().id()).isEqualTo(ricId);
        assertThat(policy.isTransient()).isTrue();

        // Put a non transient policy
        policyBody = putPolicyBody(serviceName, ricId, policyTypeName, policyInstanceId);
        restClient().put(url, policyBody).block();
        policy = policies.getPolicy(policyInstanceId);
        assertThat(policy.isTransient()).isFalse();

        url = "/policy-instances";
        String rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains policy instance ID.").contains(policyInstanceId);

        url = "/policies/" + policyInstanceId;
        rsp = restClient().get(url).block();

        assertThat(rsp).contains(policyBody);

        // Test of error codes
        url = "/policies";
        policyBody = putPolicyBody(serviceName, ricId + "XX", policyTypeName, policyInstanceId);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        policyBody = putPolicyBody(serviceName, ricId, policyTypeName + "XX", policyInstanceId);
        addPolicyType(policyTypeName + "XX", "otherRic");
        testErrorCode(restClient().put(url, policyBody), HttpStatus.NOT_FOUND);

        policyBody = putPolicyBody(serviceName, ricId, policyTypeName, policyInstanceId);
        this.rics.getRic(ricId).setState(Ric.RicState.SYNCHRONIZING);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.LOCKED);
        this.rics.getRic(ricId).setState(Ric.RicState.AVAILABLE);
    }

    @Test
    void testFineGrainedAuth() throws Exception {
        final String POLICY_ID = "policyId";
        final String RIC_ID = "ric1";
        final String TYPE_ID = "typeName";
        addPolicy(POLICY_ID, TYPE_ID, null, RIC_ID);
        assertThat(policies.size()).isEqualTo(1);

        this.applicationConfig
                .setAuthProviderUrl(baseUrl() + OpenPolicyAgentSimulatorController.ACCESS_CONTROL_URL_REJECT);
        String url = "/policy-instances";
        String rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains no policy instance ID.").contains("[]");

        url = "/policies/" + POLICY_ID;
        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        url = "/policies";
        String policyBody = putPolicyBody(null, RIC_ID, TYPE_ID, POLICY_ID, false, null);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.UNAUTHORIZED, "Not authorized");

        rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains no policy instance ID.").contains("[]");
    }

    @Test
    void testFineGrainedAuth_OPA_UNAVALIABLE() throws Exception {
        final String POLICY_ID = "policyId";
        final String RIC_ID = "ric1";
        final String TYPE_ID = "typeName";
        addPolicy(POLICY_ID, TYPE_ID, null, RIC_ID);
        assertThat(policies.size()).isEqualTo(1);

        this.applicationConfig.setAuthProviderUrl("junk");

        String url = "/policy-instances";
        String rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains no policy instance ID.").contains("[]");

        url = "/policies/" + POLICY_ID;
        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        url = "/policies";
        String policyBody = putPolicyBody(null, RIC_ID, TYPE_ID, POLICY_ID, false, null);
        testErrorCode(restClient().put(url, policyBody), HttpStatus.UNAUTHORIZED, "Not authorized");

        rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains no policy instance ID.").contains("[]");
    }
    @Test
    @DisplayName("test Put Policy No Service No Status Uri")
    void testPutPolicy_NoServiceNoStatusUri() throws Exception {
        String ricId = "ric.1";
        String policyTypeName = "type1_1.2.3";
        String policyInstanceId = "instance_1.2.3";

        addPolicyType(policyTypeName, ricId);

        // PUT a transient policy
        String url = "/policies";
        String policyBody = putPolicyBody(null, ricId, policyTypeName, policyInstanceId, true, null);
        this.rics.getRic(ricId).setState(Ric.RicState.AVAILABLE);

        restClient().put(url, policyBody).block();

        Policy policy = policies.getPolicy(policyInstanceId);
        assertThat(policy).isNotNull();
        assertThat(policy.getOwnerServiceId()).isBlank();
        assertThat(policy.getStatusNotificationUri()).isBlank();
    }

    @Test
    /**
     * Test that HttpStatus and body from failing REST call to A1 is passed on to
     * the caller.
     *
     * @throws ServiceException
     */
    @DisplayName("test Error From Ric")
    void testErrorFromRic() throws ServiceException, JsonProcessingException {
        putService("service1");
        addPolicyType("type1", "ric1");

        MockA1Client a1Client = a1ClientFactory.getOrCreateA1Client("ric1");
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String responseBody = "Refused";
        byte[] responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);

        WebClientResponseException a1Exception = new WebClientResponseException(httpStatus.value(), "statusText", null,
                responseBodyBytes, StandardCharsets.UTF_8, null);
        doReturn(Mono.error(a1Exception)).when(a1Client).putPolicy(any());

        // PUT Policy
        String putBody = putPolicyBody("service1", "ric1", "type1", "id1");
        String url = "/policies";
        testErrorCode(restClient().put(url, putBody), httpStatus, responseBody);

        // DELETE POLICY
        this.addPolicy("instance1", "type1", "service1", "ric1");
        doReturn(Mono.error(a1Exception)).when(a1Client).deletePolicy(any());
        testErrorCode(restClient().delete("/policies/instance1"), httpStatus, responseBody);

    }

    @Test
    @DisplayName("test Put Typeless Policy")
    void testPutTypelessPolicy() throws Exception {
        putService("service1");
        addPolicyType("", "ric1");
        String body = putPolicyBody("service1", "ric1", "", "id1");
        restClient().put("/policies", body).block();

        String rsp = restClient().get("/policy-instances").block();
        PolicyInfoList info = objectMapper.readValue(rsp, PolicyInfoList.class);
        assertThat(info.getPolicies()).hasSize(1);
        PolicyInfo policyInfo = info.getPolicies().iterator().next();
        assertThat(policyInfo.getPolicyId()).isEqualTo("id1");
        assertThat(policyInfo.getPolicytypeId()).isEmpty();
    }

    @Test
    @DisplayName("test Update Service")
    void testUpdateService() throws Exception {
        this.addRic("ric1");
        this.addPolicy("p", "type1", "", "ric1");

        String url = "/policies?service_id=";
        String resp = restClient().get(url).block();
        assertThat(resp).contains("[\"p\"]");

        this.addPolicy("p", "type1", "service", "ric1");
        url = "/policies?service_id=";
        resp = restClient().get(url).block();
        assertThat(resp).contains("[]");

        url = "/policies?service_id=service";
        resp = restClient().get(url).block();
        assertThat(resp).contains("[\"p\"]");
    }

    @Test
    @DisplayName("test Refuse To Update Policy")
    void testRefuseToUpdatePolicy() throws Exception {
        // Test that only the json can be changed for a already created policy
        // In this case service is attempted to be changed
        this.addRic("ric1");
        this.addRic("ricXXX");
        this.addPolicy("instance1", "type1", "service1", "ric1");
        this.addPolicy("instance2", "type1", "service1", "ricXXX");

        // Try change ric1 -> ricXXX
        String bodyWrongRic = putPolicyBody("service1", "ricXXX", "type1", "instance1");
        testErrorCode(restClient().put("/policies", bodyWrongRic), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("test Get Policy")
    void testGetPolicy() throws Exception {
        String url = "/policies/id";
        Policy policy = addPolicy("id", "typeName", "service1", "ric1");
        {
            String response = restClient().get(url).block();
            PolicyInfo policyInfo = objectMapper.readValue(response, PolicyInfo.class);
            String policyData = gson.toJson(policyInfo.getPolicyData());
            assertThat(policyData).isEqualTo(policy.getJson());
        }
        {
            policies.remove(policy);
            testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("test Delete Policy")
    void testDeletePolicy() throws Exception {
        String policyId = "id.1";
        addPolicy(policyId, "typeName", "service1", "ric1");
        assertThat(policies.size()).isEqualTo(1);

        String url = "/policies/" + policyId;
        ResponseEntity<String> entity = restClient().deleteForEntity(url).block();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(policies.size()).isZero();

        // Delete a non existing policy
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }


    @Test
    @DisplayName("test Get Policy Type")
    void testGetPolicyType() throws Exception {
        String typeId = "AC.D";
        addPolicyType(typeId, "ric1");

        waitForRicState("ric1", RicState.AVAILABLE);

        String url = "/policy-types/" + typeId;

        String response = this.restClient().get(url).block();

        PolicyTypeDefinition policyTypeDefinition = objectMapper.readValue(response, PolicyTypeDefinition.class);
        assertEquals("{\"title\":\"AC.D\"}", policyTypeDefinition.getPolicySchema());

        // Get non existing schema
        url = "/policy-types/JUNK";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    String createPolicyTypesJson(String... types) throws JsonProcessingException {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, types);
        PolicyTypeIdList ids = new PolicyTypeIdList();
        ids.setPolicytypeIds(list);
        return objectMapper.writeValueAsString(ids);
    }

    @Test
    @DisplayName("test Get Policy Types")
    void testGetPolicyTypes() throws Exception {
        String TYPE_ID_1 = "A_type1_1.9.0";
        String TYPE_ID_2 = "A_type1_2.0.0";
        String TYPE_ID_3 = "A_type1_1.5.0";
        String TYPE_ID_4 = "type3_1.9.0";
        addPolicyType(TYPE_ID_1, "ric1");
        addPolicyType(TYPE_ID_2, "ric2");
        addPolicyType(TYPE_ID_3, "ric2");
        addPolicyType(TYPE_ID_4, "ric2");

        addPolicyType("junk", "ric2");
        addPolicyType("junk_a.b.c", "ric2");

        String url = "/policy-types";
        String rsp = restClient().get(url).block();
        assertThat(rsp).contains(TYPE_ID_1, TYPE_ID_2);

        url = "/policy-types?ric_id=ric1";
        rsp = restClient().get(url).block();
        String expResp = createPolicyTypesJson(TYPE_ID_1);
        assertThat(rsp).isEqualTo(expResp);

        // Get policy types for non existing RIC
        url = "/policy-types?ric_id=ric1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // All types with a type_name
        url = "/policy-types?type_name=A_type1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains(TYPE_ID_1, TYPE_ID_2);

        // All types compatible with type1_1.5.0 (which is type1_1.9.0)
        url = "/policy-types?type_name=A_type1&&compatible_with_version=1.5.0";
        rsp = restClient().get(url).block();
        expResp = createPolicyTypesJson(TYPE_ID_3, TYPE_ID_1);
        assertThat(rsp).isEqualTo(expResp);

        url = "/policy-types?type_name=A_type1&&compatible_with_version=junk";
        testErrorCode(restClient().get(url), HttpStatus.BAD_REQUEST, "Version must contain major.minor.patch code");

        url = "/policy-types?type_name=A_type1&&compatible_with_version=a.b.c";
        testErrorCode(restClient().get(url), HttpStatus.BAD_REQUEST, "Syntax error in");

        url = "/policy-types?compatible_with_version=1.5.0";
        testErrorCode(restClient().get(url), HttpStatus.BAD_REQUEST, "type_name");
    }

    @Test
    @DisplayName("test Get Policy Instances")
    void testGetPolicyInstances() throws Exception {
        addPolicy("id1", "type1", "service1");

        String url = "/policy-instances";
        String response = restClient().get(url).block();
        logger.info(response);
        PolicyInfoList policyInfoList = objectMapper.readValue(response, PolicyInfoList.class);
        assertThat(policyInfoList.getPolicies()).hasSize(1);
        PolicyInfo policyInfo = policyInfoList.getPolicies().iterator().next();
        assertThat(policyInfo.getPolicyId()).isEqualTo("id1");
        assertThat(policyInfo.getPolicytypeId()).isEqualTo("type1");
        assertThat(policyInfo.getServiceId()).isEqualTo("service1");
    }

    @Test
    @DisplayName("test Get Policy Instances Filter")
    void testGetPolicyInstancesFilter() throws Exception {
        addPolicy("id1", "type1", "service1");
        addPolicy("id2", "type1", "service2");
        addPolicy("id3", "type2", "service1");
        addPolicy("id4", "type1_1.0.0", "service1");

        String url = "/policy-instances?policytype_id=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1")
                .contains("id2")
                .doesNotContain("id3");

        url = "/policy-instances?policytype_id=type1&service_id=service2";
        rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).doesNotContain("id1")
                .contains("id2") //
                .doesNotContain("id3");

        url = "/policy-instances?type_name=type1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains("id1") //
                .contains("id2") //
                .doesNotContain("id3")
                .contains("id4");

        // Test get policies for non existing type
        url = "/policy-instances?policytype_id=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policies for non existing RIC
        url = "/policy-instances?ric_id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("test Get Policy Ids Filter")
    void testGetPolicyIdsFilter() throws Exception {
        addPolicy("id1", "type1", "service1", "ric1");
        addPolicy("id2", "type1", "service2", "ric1");
        addPolicy("id3", "type2", "service1", "ric1");
        addPolicy("id4", "type1_1.0.0", "service1");

        String url = "/policies?policytype_id=type1";
        String rsp = restClient().get(url).block();
        logger.info(rsp);
        assertThat(rsp).contains("id1")
                .contains("id2")
                .doesNotContain("id3");

        url = "/policies?policytype_id=type1&service_id=service1&ric=ric1";
        rsp = restClient().get(url).block();
        PolicyIdList respList = objectMapper.readValue(rsp, PolicyIdList.class);
        assertThat(respList.getPolicyIds().iterator().next()).isEqualTo("id1");

        url = "/policies?type_name=type1&service_id=service1";
        rsp = restClient().get(url).block();
        assertThat(rsp).contains("id1").contains("id4");
        assertThat(objectMapper.readValue(rsp, PolicyIdList.class).getPolicyIds()).hasSize(2);

        // Test get policy ids for non existing type
        url = "/policies?policytype_id=type1XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // Test get policy ids for non existing RIC
        url = "/policies?ric_id=XXX";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);
    }


    @Test
    @DisplayName("test Put And Get Service")
    void testPutAndGetService() throws Exception {
        // PUT
        String serviceName = "ac.dc";
        putService(serviceName, 0, HttpStatus.CREATED);
        putService(serviceName, 0, HttpStatus.OK);

        // GET one service
        String url = "/services?service_id=" + serviceName;
        String rsp = restClient().get(url).block();
        ServiceStatusList info = objectMapper.readValue(rsp, ServiceStatusList.class);
        assertThat(info.getServiceList()).hasSize(1);
        ServiceStatus status = info.getServiceList().iterator().next();
        assertThat(status.getKeepAliveIntervalSeconds()).isZero();
        assertThat(status.getServiceId()).isEqualTo(serviceName);

        // GET (all)
        url = "/services";
        rsp = restClient().get(url).block();
        assertThat(rsp).as("Response contains service name").contains(serviceName);
        logger.info(rsp);

        // Keep alive
        url = "/services/" + serviceName + "/keepalive";
        ResponseEntity<?> entity = restClient().putForEntity(url).block();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE service
        assertThat(services.size()).isEqualTo(1);
        url = "/services/" + serviceName;
        restClient().delete(url).block();
        assertThat(services.size()).isZero();

        // Keep alive, no registered service
        testErrorCode(restClient().put("/services/junk/keepalive", ""), HttpStatus.NOT_FOUND);

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
    @DisplayName("test Service Supervision")
    void testServiceSupervision() throws Exception {
        putService("service1", 2, HttpStatus.CREATED);
        addPolicyType("type1", "ric1");

        String policyBody = putPolicyBody("service1", "ric1", "type1", "instance1");
        restClient().put("/policies", policyBody).block();

        assertThat(policies.size()).isEqualTo(1);
        assertThat(services.size()).isEqualTo(1);

        // Timeout after ~2 second
        await().untilAsserted(() -> assertThat(policies.size()).isZero());
        assertThat(services.size()).isZero();
    }

    @Test
    @DisplayName("test Get Policy Status")
    void testGetPolicyStatus() throws Exception {
        addPolicy("id", "typeName", "service1", "ric1");
        assertThat(policies.size()).isEqualTo(1);

        String url = "/policies/id/status";
        String response = restClient().get(url).block();
        PolicyStatusInfo info = objectMapper.readValue(response, PolicyStatusInfo.class);
        assertThat(info.getStatus()).isEqualTo("OK");

        // GET non existing policy status
        url = "/policies/XXX/status";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND);

        // GET STATUS, the NearRT RIC returns error
        MockA1Client a1Client = a1ClientFactory.getOrCreateA1Client("ric1");
        url = "/policies/id/status";
        WebClientResponseException a1Exception = new WebClientResponseException(404, "", null, null, null);
        doReturn(Mono.error(a1Exception)).when(a1Client).getPolicyStatus(any());
        response = restClient().get(url).block();
        info = objectMapper.readValue(response, PolicyStatusInfo.class);
        assertThat(info.getStatus()).hasToString("{}");
    }

    @Test
    @DisplayName("test Get Service Status")
    void testGetServiceStatus() throws Exception {
        String url = "/status";
        String rsp = restClient().get(url).block();
        assertThat(rsp).contains("success");

        rsp = restClient(baseUrl(), false).get(url).block(); // V1 status is used by a readinessProbe
        assertThat(rsp).isEqualTo("success");
    }

    @Test
    @DisplayName("test Service Notification")
    void testServiceNotification() throws Exception {

        final String AUTH_TOKEN = "testToken";
        Path authFile = Files.createTempFile("pmsTestAuthToken", ".txt");
        Files.write(authFile, AUTH_TOKEN.getBytes());
        this.securityContext.setAuthTokenFilePath(authFile);

        putService("junkService");
        Service junkService = this.services.get("junkService");
        junkService.setCallbackUrl("https://junk");
        putService("service");

        Ric ric = addRic("ric1");
        ric.setState(Ric.RicState.UNAVAILABLE);
        supervision.checkAllRics();
        waitForRicState("ric1", RicState.AVAILABLE);

        final RappSimulatorController.TestResults receivedCallbacks = rAppSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(receivedCallbacks.getReceivedInfo()).hasSize(1));
        ServiceCallbackInfo callbackInfo = receivedCallbacks.getReceivedInfo().get(0);
        assertThat(callbackInfo.ricId).isEqualTo("ric1");
        assertThat(callbackInfo.eventType).isEqualTo(ServiceCallbackInfo.EventType.AVAILABLE);

        var headers = receivedCallbacks.receivedHeaders.get(0);
        assertThat(headers).containsEntry("authorization", "Bearer " + AUTH_TOKEN);

        Files.delete(authFile);
    }

    private Policy addPolicy(String id, String typeName, String service, String ric) throws ServiceException {
        addRic(ric);
        Policy policy = Policy.builder()
                .id(id)
                .json(gson.toJson(jsonString()))
                .ownerServiceId(service)
                .ric(rics.getRic(ric))
                .type(addPolicyType(typeName, ric))
                .lastModified(Instant.now())
                .isTransient(false)
                .statusNotificationUri("/policy-status?id=XXX")
                .build();
        policies.put(policy);
        return policy;
    }

    private Policy addPolicy(String id, String typeName, String service) throws ServiceException {
        return addPolicy(id, typeName, service, "ric");
    }

    private String createServiceJson(String name, long keepAliveIntervalSeconds) throws JsonProcessingException {
        String callbackUrl = baseUrl() + RappSimulatorController.SERVICE_CALLBACK_URL;
        return createServiceJson(name, keepAliveIntervalSeconds, callbackUrl);
    }

    private String createServiceJson(String name, long keepAliveIntervalSeconds, String url) throws JsonProcessingException {
        ServiceRegistrationInfo service = new ServiceRegistrationInfo(name)
                .keepAliveIntervalSeconds(keepAliveIntervalSeconds)
                .callbackUrl(url);

        return objectMapper.writeValueAsString(service);
    }

    private void putService(String name) throws JsonProcessingException {
        putService(name, 0, null);
    }

    private void putService(String name, long keepAliveIntervalSeconds, @Nullable HttpStatus expectedStatus) throws JsonProcessingException {
        String url = "/services";
        String body = createServiceJson(name, keepAliveIntervalSeconds);
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        if (expectedStatus != null) {
            assertEquals(expectedStatus, resp.getStatusCode(), "");
        }
    }

    private Map<String,String> jsonString() {
        Map<String,String> policyDataInMap = new HashMap<>();
        policyDataInMap.put("servingCellNrcgi","1");
        return policyDataInMap;
    }

    @Test
    @DisplayName("test Concurrency")
    void testConcurrency() throws Exception {
        this.applicationConfig.setAuthProviderUrl("");
        logger.info("Concurrency test starting");
        final Instant startTime = Instant.now();
        List<Thread> threads = new ArrayList<>();
        List<ConcurrencyTestRunnable> tests = new ArrayList<>();
        a1ClientFactory.setResponseDelay(Duration.ofMillis(2));
        addRic("ric");
        addPolicyType("type1", "ric");
        addPolicyType("type2", "ric");

        final String NON_RESPONDING_RIC = "NonRespondingRic";
        Ric nonRespondingRic = addRic(NON_RESPONDING_RIC);
        MockA1Client a1Client = a1ClientFactory.getOrCreateA1Client(NON_RESPONDING_RIC);
        a1Client.setErrorInject("errorInject");

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

        assertThat(nonRespondingRic.getState()).isEqualTo(RicState.UNAVAILABLE);
        nonRespondingRic.setState(RicState.AVAILABLE);
    }

    private AsyncRestClient restClient(String baseUrl, boolean useTrustValidation) {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        config = WebClientConfig.builder()
                .keyStoreType(config.getKeyStoreType())
                .keyStorePassword(config.getKeyStorePassword())
                .keyStore(config.getKeyStore())
                .keyPassword(config.getKeyPassword())
                .isTrustStoreUsed(useTrustValidation)
                .trustStore(config.getTrustStore())
                .trustStorePassword(config.getTrustStorePassword())
                .httpProxyConfig(config.getHttpProxyConfig())
                .build();

        AsyncRestClientFactory f = new AsyncRestClientFactory(config, new SecurityContext(""));
        return f.createRestClientNoHttpProxy(baseUrl);

    }

    private String baseUrl() {
        return "https://localhost:" + port;
    }

    private AsyncRestClient restClient(boolean useTrustValidation) {
        return restClient(baseUrl() + Consts.V2_API_ROOT, useTrustValidation);
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
        StepVerifier.create(request)
                .expectSubscription()
                .expectErrorMatches(
                        t -> checkWebClientError(t, expStatus, responseContains, expectApplicationProblemJsonMediaType))
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
        String body = responseException.getResponseBodyAsString();
        assertThat(body).contains(responseContains);
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);

        if (expectApplicationProblemJsonMediaType) {
            assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return true;
    }

    private MockA1Client getA1Client(String ricId) throws ServiceException {
        return a1ClientFactory.getOrCreateA1Client(ricId);
    }

    private PolicyType createPolicyType(String policyTypeName) {
        return PolicyType.builder()
                .id(policyTypeName)
                .schema("{\"title\":\"" + policyTypeName + "\"}")
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

    private RicConfig ricConfig(String ricId, String managedElement) {
        List<String> mes = new ArrayList<>();
        if (managedElement != null) {
            mes.add(managedElement);
        }
        return RicConfig.builder()
                .ricId(ricId)
                .baseUrl(ricId)
                .managedElementIds(mes)
                .build();
    }

    private Ric addRic(String ricId, String managedElement) {
        if (rics.get(ricId) != null) {
            return rics.get(ricId);
        }

        RicConfig conf = ricConfig(ricId, managedElement);
        Ric ric = new Ric(conf);
        ric.setState(Ric.RicState.AVAILABLE);
        this.rics.put(ric);
        return ric;
    }

}
