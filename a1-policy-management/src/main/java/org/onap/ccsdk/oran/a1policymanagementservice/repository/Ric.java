/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import lombok.Getter;
import lombok.Setter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.repository.Ric.RicState;

/**
 * Represents the dynamic information about a Near-RT RIC.
 */
public class Ric {

    @Setter
    private RicConfig ricConfig;
    private RicState state = RicState.UNAVAILABLE;
    private Map<String, PolicyType> supportedPolicyTypes = new HashMap<>();
    @Getter
    @Setter
    private A1ProtocolType protocolVersion = A1ProtocolType.UNKNOWN;

    @Getter
    private final Lock lock = new Lock();

    /**
     * Creates the Ric. Initial state is {@link RicState.UNDEFINED}.
     *
     * @param ricConfig The {@link RicConfig} for this Ric.
     */
    public Ric(RicConfig ricConfig) {
        this.ricConfig = ricConfig;
    }

    public String name() {
        return ricConfig.name();
    }

    public RicConfig getConfig() {
        return this.ricConfig;
    }

    public synchronized RicState getState() {
        return this.state;
    }

    public synchronized void setState(RicState state) {
        this.state = state;
    }

    /**
     * Gets the nodes managed by this Ric.
     *
     * @return a vector containing the nodes managed by this Ric.
     */
    public synchronized Collection<String> getManagedElementIds() {
        return ricConfig.managedElementIds();
    }

    /**
     * Determines if the given node is managed by this Ric.
     *
     * @param managedElementId the node name to check.
     * @return true if the given node is managed by this Ric.
     */
    public synchronized boolean isManaging(String managedElementId) {
        return ricConfig.managedElementIds().contains(managedElementId);
    }

    /**
     * Gets the policy types supported by this Ric.
     *
     * @return the policy types supported by this Ric in an unmodifiable list.
     */
    public synchronized Collection<PolicyType> getSupportedPolicyTypes() {
        return new Vector<>(supportedPolicyTypes.values());
    }

    public synchronized Collection<String> getSupportedPolicyTypeNames() {
        return new Vector<>(supportedPolicyTypes.keySet());
    }

    /**
     * Adds a policy type as supported by this Ric.
     *
     * @param type the policy type to support.
     */
    public synchronized void addSupportedPolicyType(PolicyType type) {
        supportedPolicyTypes.put(type.name(), type);
    }

    /**
     * Removes all policy type as supported by this Ric.
     */
    public synchronized void clearSupportedPolicyTypes() {
        supportedPolicyTypes.clear();
    }

    /**
     * Checks if a type is supported by this Ric.
     *
     * @param typeName the name of the type to check if it is supported.
     *
     * @return true if the given type is supported by this Ric, false otherwise.
     */
    public synchronized boolean isSupportingType(String typeName) {
        return supportedPolicyTypes.containsKey(typeName);
    }

    @Override
    public synchronized String toString() {
        return Ric.class.getSimpleName() + ": " + "name: " + name() + ", state: " + state + ", baseUrl: "
            + ricConfig.baseUrl() + ", managedNodes: " + ricConfig.managedElementIds();
    }

    /**
     * Represents the states possible for a Ric.
     */
    public enum RicState {
        /**
         * The Policy Management Service's view of the Ric may be inconsistent.
         */
        UNAVAILABLE,
        /**
         * The normal state. Policies can be configured.
         */
        AVAILABLE,
        /**
         * The Policy Management Service is synchronizing the view of the Ric.
         */
        SYNCHRONIZING,

        /**
         * A consistency check between the Policy Management Service and the Ric is done
         */
        CONSISTENCY_CHECK
    }
}
