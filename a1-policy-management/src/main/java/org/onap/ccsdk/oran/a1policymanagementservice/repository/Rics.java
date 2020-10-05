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
import java.util.Optional;
import java.util.Vector;

import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.springframework.lang.Nullable;

/**
 * Dynamic representation of all Rics in the system.
 */
public class Rics {
    Map<String, Ric> registeredRics = new HashMap<>();

    public synchronized void put(Ric ric) {
        registeredRics.put(ric.id(), ric);
    }

    public synchronized Collection<Ric> getRics() {
        return new Vector<>(registeredRics.values());
    }

    public synchronized Ric getRic(String ricId) throws ServiceException {
        Ric ric = registeredRics.get(ricId);
        if (ric == null) {
            throw new ServiceException("Could not find ric: " + ricId);
        }
        return ric;
    }

    public synchronized Ric get(String ricId) {
        return registeredRics.get(ricId);
    }

    public synchronized @Nullable Ric remove(String ricId) {
        return registeredRics.remove(ricId);
    }

    public synchronized int size() {
        return registeredRics.size();
    }

    public synchronized void clear() {
        this.registeredRics.clear();
    }

    public synchronized Optional<Ric> lookupRicForManagedElement(String managedElementId) {
        for (Ric ric : this.registeredRics.values()) {
            if (ric.getManagedElementIds().contains(managedElementId)) {
                return Optional.of(ric);
            }
        }
        return Optional.empty();
    }
}
