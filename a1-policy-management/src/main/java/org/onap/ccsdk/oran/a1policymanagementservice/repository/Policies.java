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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.EntityNotFoundException;

public class Policies {
    private Map<String, Policy> policiesId = new HashMap<>();
    private Map<String, Map<String, Policy>> policiesRic = new HashMap<>();
    private Map<String, Map<String, Policy>> policiesService = new HashMap<>();
    private Map<String, Map<String, Policy>> policiesType = new HashMap<>();

    public synchronized void put(Policy policy) {
        policiesId.put(policy.id(), policy);
        multiMapPut(policiesRic, policy.ric().id(), policy);
        multiMapPut(policiesService, policy.ownerServiceId(), policy);
        multiMapPut(policiesType, policy.type().id(), policy);
    }

    private void multiMapPut(Map<String, Map<String, Policy>> multiMap, String key, Policy value) {
        multiMap.computeIfAbsent(key, k -> new HashMap<>()).put(value.id(), value);
    }

    private void multiMapRemove(Map<String, Map<String, Policy>> multiMap, String key, Policy value) {
        Map<String, Policy> map = multiMap.get(key);
        if (map != null) {
            map.remove(value.id());
            if (map.isEmpty()) {
                multiMap.remove(key);
            }
        }
    }

    private Collection<Policy> multiMapGet(Map<String, Map<String, Policy>> multiMap, String key) {
        Map<String, Policy> map = multiMap.get(key);
        if (map == null) {
            return Collections.emptyList();
        }
        return new Vector<>(map.values());
    }

    public synchronized boolean containsPolicy(String id) {
        return policiesId.containsKey(id);
    }

    public synchronized Policy get(String id) {
        return policiesId.get(id);
    }

    public synchronized Policy getPolicy(String id) throws EntityNotFoundException {
        Policy p = policiesId.get(id);
        if (p == null) {
            throw new EntityNotFoundException("Could not find policy: " + id);
        }
        return p;
    }

    public synchronized Collection<Policy> getAll() {
        return new Vector<>(policiesId.values());
    }

    public synchronized Collection<Policy> getForService(String service) {
        return multiMapGet(policiesService, service);
    }

    public synchronized Collection<Policy> getForRic(String ric) {
        return multiMapGet(policiesRic, ric);
    }

    public synchronized Collection<Policy> getForType(String type) {
        return multiMapGet(policiesType, type);
    }

    public synchronized Policy removeId(String id) {
        Policy p = policiesId.get(id);
        if (p != null) {
            remove(p);
        }
        return p;
    }

    public synchronized void remove(Policy policy) {
        policiesId.remove(policy.id());
        multiMapRemove(policiesRic, policy.ric().id(), policy);
        multiMapRemove(policiesService, policy.ownerServiceId(), policy);
        multiMapRemove(policiesType, policy.type().id(), policy);
    }

    public synchronized void removePoliciesForRic(String ricId) {
        Collection<Policy> policiesForRic = getForRic(ricId);
        for (Policy policy : policiesForRic) {
            remove(policy);
        }
    }

    public synchronized int size() {
        return policiesId.size();
    }

    public synchronized void clear() {
        while (policiesId.size() > 0) {
            Set<String> keys = policiesId.keySet();
            removeId(keys.iterator().next());
        }
    }
}
