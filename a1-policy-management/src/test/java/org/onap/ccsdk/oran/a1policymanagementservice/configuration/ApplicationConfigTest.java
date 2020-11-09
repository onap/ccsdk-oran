/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020 Nordix Foundation. All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfig.RicConfigUpdate;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.ApplicationConfigParser.ConfigParserResult;

@ExtendWith(MockitoExtension.class)
class ApplicationConfigTest {

    private static final ImmutableRicConfig RIC_CONFIG_1 = ImmutableRicConfig.builder() //
            .ricId("ric1") //
            .baseUrl("ric1_url") //
            .managedElementIds(new Vector<>()) //
            .controllerName("") //
            .build();

    private static final ImmutableRicConfig RIC_CONFIG_2 = ImmutableRicConfig.builder() //
            .ricId("ric2") //
            .baseUrl("ric1_url") //
            .managedElementIds(new Vector<>()) //
            .controllerName("") //
            .build();

    private static final ImmutableRicConfig RIC_CONFIG_3 = ImmutableRicConfig.builder() //
            .ricId("ric3") //
            .baseUrl("ric1_url") //
            .managedElementIds(new Vector<>()) //
            .controllerName("") //
            .build();

    ConfigParserResult configParserResult(RicConfig... rics) {
        return ImmutableConfigParserResult.builder() //
                .ricConfigs(Arrays.asList(rics)) //
                .dmaapConsumerTopicUrl("dmaapConsumerTopicUrl") //
                .dmaapProducerTopicUrl("dmaapProducerTopicUrl") //
                .controllerConfigs(new HashMap<>()) //
                .build();
    }

    @Test
    void addRics() throws Exception {
        ApplicationConfig appConfigUnderTest = new ApplicationConfig();

        List<RicConfigUpdate> update = appConfigUnderTest.setConfiguration(configParserResult(RIC_CONFIG_1)) //
                .collectList().block();
        assertEquals(1, update.size());
        assertEquals(RicConfigUpdate.Type.ADDED, update.get(0).getType());
        assertTrue(appConfigUnderTest.getRicConfigs().contains(RIC_CONFIG_1), "Ric not added to configurations.");

        assertEquals(RIC_CONFIG_1, appConfigUnderTest.getRic(RIC_CONFIG_1.ricId()),
                "Not correct Ric retrieved from configurations.");

        update = appConfigUnderTest.setConfiguration(configParserResult(RIC_CONFIG_1)).collectList().block();
        assertEquals(0, update.size());

        update = appConfigUnderTest.setConfiguration(configParserResult(RIC_CONFIG_1, RIC_CONFIG_2)).collectList()
                .block();
        assertEquals(1, update.size());
        assertEquals(RicConfigUpdate.Type.ADDED, update.get(0).getType());

    }

    @Test
    void changedRic() throws Exception {
        ApplicationConfig appConfigUnderTest = new ApplicationConfig();

        List<RicConfigUpdate> update = appConfigUnderTest
                .setConfiguration(configParserResult(RIC_CONFIG_1, RIC_CONFIG_2, RIC_CONFIG_3)).collectList().block();
        assertEquals(3, update.size());

        ImmutableRicConfig changedRicConfig = ImmutableRicConfig.builder() //
                .ricId(RIC_CONFIG_1.ricId()) //
                .baseUrl("changed_ric1_url") //
                .managedElementIds(new Vector<>()) //
                .controllerName("") //
                .build();

        update = appConfigUnderTest.setConfiguration(configParserResult(changedRicConfig, RIC_CONFIG_2, RIC_CONFIG_3))
                .collectList().block();
        assertEquals(1, update.size());

        assertEquals(RicConfigUpdate.Type.CHANGED, update.get(0).getType());
        assertEquals(changedRicConfig, appConfigUnderTest.getRic(RIC_CONFIG_1.ricId()),
                "Changed Ric not retrieved from configurations.");
    }

    @Test
    void removedRic() {
        ApplicationConfig appConfigUnderTest = new ApplicationConfig();

        List<RicConfigUpdate> update = appConfigUnderTest
                .setConfiguration(configParserResult(RIC_CONFIG_1, RIC_CONFIG_2, RIC_CONFIG_3)).collectList().block();
        assertEquals(3, update.size());

        update = appConfigUnderTest.setConfiguration(configParserResult(RIC_CONFIG_2, RIC_CONFIG_3)) //
                .collectList() //
                .block();
        assertEquals(1, update.size());
        assertEquals(RicConfigUpdate.Type.REMOVED, update.get(0).getType());
        assertEquals(RIC_CONFIG_1, update.get(0).getRicConfig());
        assertEquals(2, appConfigUnderTest.getRicConfigs().size(), "Ric not deleted from configurations.");
    }

}
