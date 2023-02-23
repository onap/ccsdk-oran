/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;

class ApplicationConfigParserTest {

    ApplicationConfig applicationConfigMock = spy(new ApplicationConfig());
    ApplicationConfigParser parserUnderTest = new ApplicationConfigParser(applicationConfigMock);

    @Test
    @DisplayName("test when Correct Config")
    void whenCorrectConfig() throws Exception {
        JsonObject jsonRootObject = getJsonRootObject();

        when(applicationConfigMock.getConfigurationFileSchemaPath())
                .thenReturn("/application_configuration_schema.json");

        ApplicationConfigParser.ConfigParserResult result = parserUnderTest.parse(jsonRootObject);

        Map<String, ControllerConfig> controllers = result.getControllerConfigs();
        assertEquals(1, controllers.size(), "size");
        ControllerConfig expectedControllerConfig = ControllerConfig.builder() //
                .baseUrl("http://localhost:8083/") //
                .name("controller1") //
                .userName("user") //
                .password("password") //
                .build(); //

        ControllerConfig actual = controllers.get("controller1");
        assertEquals(expectedControllerConfig, actual, "controller contents");

        assertEquals(2, result.getRicConfigs().size());
    }

    private JsonObject getJsonRootObject() throws JsonIOException, JsonSyntaxException, IOException {
        JsonObject rootObject = JsonParser.parseReader(new InputStreamReader(getCorrectJson())).getAsJsonObject();
        return rootObject;
    }

    private static InputStream getCorrectJson() throws IOException {
        URL url = ApplicationConfigParser.class.getClassLoader().getResource("test_application_configuration.json");
        String string = Resources.toString(url, Charsets.UTF_8);
        return new ByteArrayInputStream((string.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("test when Wrong Member Name In Object")
    void whenWrongMemberNameInObject() throws Exception {
        JsonObject jsonRootObject = getJsonRootObject();
        JsonObject json = jsonRootObject.getAsJsonObject("config");
        json.remove("ric");
        final String message = "Could not find member: [ric] in: " + json;

        Exception actualException = assertThrows(ServiceException.class, () -> parserUnderTest.parse(jsonRootObject));

        assertEquals(message, actualException.getMessage(), "Wrong error message when wrong member name in object");
    }

    @Test
    @DisplayName("test schema Validation Error")
    void schemaValidationError() throws Exception {
        when(applicationConfigMock.getConfigurationFileSchemaPath())
                .thenReturn("application_configuration_schema.json");
        JsonObject jsonRootObject = getJsonRootObject();
        JsonObject json = jsonRootObject.getAsJsonObject("config");
        json.remove("ric");

        Exception actualException = assertThrows(ServiceException.class, () -> parserUnderTest.parse(jsonRootObject));

        assertThat(actualException.getMessage()).contains("Json schema validation failure");
    }
}
