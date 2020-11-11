/*-
 * ========================LICENSE_START=================================
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ConfigurationFileWriteException;
import org.onap.ccsdk.oran.a1policymanagementservice.utils.LoggingUtils;

@ExtendWith(MockitoExtension.class)
class ConfigurationFileTest {
    @Mock
    ApplicationConfig applicationConfigMock;

    @TempDir
    public File temporaryFolder;

    @Test
    void writeFileWithError_shouldThrowExceptionAndLogError() throws Exception {
        File tempJsonFile = new File(temporaryFolder, "config.json");
        String filePath = tempJsonFile.getAbsolutePath();

        ConfigurationFile configFileUnderTestSpy = spy(new ConfigurationFile(applicationConfigMock));

        when(applicationConfigMock.getLocalConfigurationFilePath()).thenReturn(filePath);
        doThrow(new IOException("Error")).when(configFileUnderTestSpy).getFileWriter(any(String.class));

        ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(ConfigurationFile.class, Level.ERROR);
        Exception actualException = assertThrows(ConfigurationFileWriteException.class,
                () -> configFileUnderTestSpy.writeFile(new JsonObject()));

        assertThat(actualException.getMessage())
                .startsWith("Local configuration file not written due to internal problem.");

        assertThat(logAppender.list.get(0).getFormattedMessage()).startsWith("Local configuration file not written");
    }

    @Test
    void writeAndReadFile_shouldBeOk() throws Exception {
        File tempJsonFile = new File(temporaryFolder, "config.json");
        String filePath = tempJsonFile.getAbsolutePath();

        ConfigurationFile configFileUnderTest = new ConfigurationFile(applicationConfigMock);

        JsonObject content = JsonParser.parseString("{\"test\":\"test\"}").getAsJsonObject();

        when(applicationConfigMock.getLocalConfigurationFilePath()).thenReturn(filePath);

        configFileUnderTest.writeFile(content);

        Optional<JsonObject> readContent = configFileUnderTest.readFile();

        assertThat(readContent).isNotEmpty().hasValue(content);
    }

    @Test
    void readWhenFileMissing_shouldReturnEmpty() {
        ConfigurationFile configFileUnderTest = new ConfigurationFile(applicationConfigMock);

        String filePath = "configFile.json";
        when(applicationConfigMock.getLocalConfigurationFilePath()).thenReturn(filePath);

        Optional<JsonObject> readContent = configFileUnderTest.readFile();

        assertThat(readContent).isEmpty();
    }

    @Test
    void readWhenFileWithIoError_shouldReturnEmptyAndLogError() throws Exception {
        File tempJsonFile = new File(temporaryFolder, "config.json");
        String filePath = tempJsonFile.getAbsolutePath();
        Files.write(tempJsonFile.toPath(), "".getBytes());

        ConfigurationFile configFileUnderTest = new ConfigurationFile(applicationConfigMock);

        when(applicationConfigMock.getLocalConfigurationFilePath()).thenReturn(filePath);

        ListAppender<ILoggingEvent> logAppender = LoggingUtils.getLogListAppender(ConfigurationFile.class, Level.ERROR);
        Optional<JsonObject> readContent = configFileUnderTest.readFile();

        assertThat(readContent).isEmpty();

        assertThat(logAppender.list.get(0).getFormattedMessage())
                .isEqualTo("Local configuration file not read: " + filePath + ", Not a JSON Object: null");
    }
}
