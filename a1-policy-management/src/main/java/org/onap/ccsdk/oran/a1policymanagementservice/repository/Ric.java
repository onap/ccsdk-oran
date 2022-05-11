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

package org.onap.ccsdk.oran.a1policymanagementservice.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import lombok.Getter;
import lombok.Setter;

import org.onap.ccsdk.oran.a1policymanagementservice.clients.A1Client.A1ProtocolType;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.RicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds information about a Near-RT RIC.
 */
public class Ric {
    private static final Logger logger = LoggerFactory.getLogger(Ric.class);

    @Setter
    private RicConfig ricConfig;
    private RicState state = RicState.UNAVAILABLE;
    private Map<String, PolicyType> supportedPolicyTypes = new HashMap<>();

    @Setter
    private A1ProtocolType protocolVersion = A1ProtocolType.UNKNOWN;

    @Getter
    private final Lock lock;

    /**
     * Creates the Ric. Initial state is {@link RicState.UNDEFINED}.
     *
     * @param ricConfig The {@link RicConfig} for this Ric.
     */
    public Ric(RicConfig ricConfig) {
        this.ricConfig = ricConfig;
        this.lock = new Lock(ricConfig.getRicId());
    }

    public String id() {
        return ricConfig.getRicId();
    }

    public RicConfig getConfig() {
        return this.ricConfig;
    }

    public synchronized RicState getState() {
        return this.state;
    }

    public synchronized void setState(RicState state) {
        logger.debug("Ric {} state set to {}", getConfig().getRicId(), state);
        this.state = state;
    }

    public synchronized A1ProtocolType getProtocolVersion() {
        if (this.ricConfig.getCustomAdapterClass().isEmpty()) {
            return this.protocolVersion;
        } else {
            return A1ProtocolType.CUSTOM_PROTOCOL;
        }
    }

    /**
     * Gets the nodes managed by this Ric.
     *
     * @return a vector containing the nodes managed by this Ric.
     */
    public synchronized Collection<String> getManagedElementIds() {
        return new Vector<>(ricConfig.getManagedElementIds());
    }

    /**
     * Determines if the given node is managed by this Ric.
     *
     * @param managedElementId the node name to check.
     * @return true if the given node is managed by this Ric.
     */
    public synchronized boolean isManaging(String managedElementId) {
        return ricConfig.getManagedElementIds().contains(managedElementId);
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
        supportedPolicyTypes.put(type.getId(), type);
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
     * @param typeId the identity of the type to check if it is supported.
     *
     * @return true if the given type is supported by this Ric, false otherwise.
     */
    public synchronized boolean isSupportingType(String typeId) {
        return supportedPolicyTypes.containsKey(typeId);
    }

    @Override
    public synchronized String toString() {
        return Ric.class.getSimpleName() + ": " + "name: " + id() + ", state: " + state + ", baseUrl: "
                + ricConfig.getBaseUrl() + ", managedNodes: " + ricConfig.getManagedElementIds();
    }

    /**
     * Represents the states possible for a Ric.
     */
    public enum RicState {
        /**
         * The Policy Management Service's view of the Near-RT RIC may be inconsistent.
         */
        UNAVAILABLE,
        /**
         * The normal state. Policies can be configured.
         */
        AVAILABLE,
        /**
         * The Policy Management Service is synchronizing the view of the Near-RT RIC.
         */
        SYNCHRONIZING,

        /**
         * A consistency check between the Policy Management Service and the Near-RT RIC
         * is done
         */
        CONSISTENCY_CHECK
    }
}
