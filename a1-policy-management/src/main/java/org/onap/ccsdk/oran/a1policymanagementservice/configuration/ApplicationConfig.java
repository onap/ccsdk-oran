/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig.HttpProxyConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import reactor.core.publisher.Flux;
import reactor.netty.transport.ProxyProvider;

@EnableConfigurationProperties
public class ApplicationConfig {

    @Getter
    @Value("${app.filepath}")
    private String localConfigurationFilePath;

    @Getter
    @Value("${app.config-file-schema-path:}")
    private String configurationFileSchemaPath;

    @Getter
    @Value("${app.vardata-directory:null}")
    private String vardataDirectory;

    @Value("${server.ssl.key-store-type}")
    private String sslKeyStoreType = "";

    @Value("${server.ssl.key-store-password}")
    private String sslKeyStorePassword = "";

    @Value("${server.ssl.key-store}")
    private String sslKeyStore = "";

    @Value("${server.ssl.key-password}")
    private String sslKeyPassword = "";

    @Value("${app.webclient.trust-store-used}")
    private boolean sslTrustStoreUsed = false;

    @Value("${app.webclient.trust-store-password}")
    private String sslTrustStorePassword = "";

    @Value("${app.webclient.trust-store}")
    private String sslTrustStore = "";

    @Value("${app.webclient.http.proxy-host:}")
    private String httpProxyHost = "";

    @Value("${app.webclient.http.proxy-port:0}")
    private int httpProxyPort = 0;

    @Value("${app.webclient.http.proxy-type:HTTP}")
    private String httpProxyType = "HTTP";

    @Getter
    @Value("${app.s3.endpointOverride:}")
    private String s3EndpointOverride;

    @Getter
    @Value("${app.s3.accessKeyId:}")
    private String s3AccessKeyId;

    @Getter
    @Value("${app.s3.secretAccessKey:}")
    private String s3SecretAccessKey;

    @Getter
    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Getter
    @Setter
    @Value("${app.authorization-provider:}")
    private String authProviderUrl;

    @Getter
    @Setter
    @Value("${app.database-enabled:}")
    private boolean databaseEnabled;

    public enum ValidateSchema {
        NONE,
        INFO,
        WARN,
        FAIL
    }

    @Getter
    @Setter
    @Value("${app.validate-schema:NONE}")
    private ValidateSchema validateSchema;

    private Map<String, RicConfig> ricConfigs = new HashMap<>();

    private WebClientConfig webClientConfig = null;

    public synchronized Collection<RicConfig> getRicConfigs() {
        return this.ricConfigs.values();
    }

    public WebClientConfig getWebClientConfig() {
        if (this.webClientConfig == null) {
            HttpProxyConfig httpProxyConfig = HttpProxyConfig.builder() //
                    .httpProxyHost(this.httpProxyHost) //
                    .httpProxyPort(this.httpProxyPort) //
                    .httpProxyType(ProxyProvider.Proxy.valueOf(this.httpProxyType)) //
                    .build();

            this.webClientConfig = WebClientConfig.builder() //
                    .keyStoreType(this.sslKeyStoreType) //
                    .keyStorePassword(this.sslKeyStorePassword) //
                    .keyStore(this.sslKeyStore) //
                    .keyPassword(this.sslKeyPassword) //
                    .isTrustStoreUsed(this.sslTrustStoreUsed) //
                    .trustStore(this.sslTrustStore) //
                    .trustStorePassword(this.sslTrustStorePassword) //
                    .httpProxyConfig(httpProxyConfig) //
                    .build();
        }
        return this.webClientConfig;
    }

    public synchronized RicConfig getRic(String ricId) throws ServiceException {
        RicConfig ricConfig = this.ricConfigs.get(ricId);
        if (ricConfig == null) {
            throw new ServiceException("Could not find ric configuration: " + ricId);
        }
        return ricConfig;
    }

    public static class RicConfigUpdate {
        public enum Type {
            ADDED, CHANGED, REMOVED
        }

        @Getter
        private final RicConfig ricConfig;
        @Getter
        private final Type type;

        public RicConfigUpdate(RicConfig config, Type event) {
            this.ricConfig = config;
            this.type = event;
        }
    }

    public synchronized Flux<RicConfigUpdate> setConfiguration(
            ApplicationConfigParser.ConfigParserResult parserResult) {

        Collection<RicConfigUpdate> modifications = new ArrayList<>();

        Map<String, RicConfig> newRicConfigs = new HashMap<>();
        for (RicConfig newConfig : parserResult.getRicConfigs()) {
            RicConfig oldConfig = this.ricConfigs.get(newConfig.getRicId());
            this.ricConfigs.remove(newConfig.getRicId());
            if (oldConfig == null) {
                newRicConfigs.put(newConfig.getRicId(), newConfig);
                modifications.add(new RicConfigUpdate(newConfig, RicConfigUpdate.Type.ADDED));
            } else if (!newConfig.equals(oldConfig)) {
                modifications.add(new RicConfigUpdate(newConfig, RicConfigUpdate.Type.CHANGED));
                newRicConfigs.put(newConfig.getRicId(), newConfig);
            } else {
                newRicConfigs.put(oldConfig.getRicId(), oldConfig);
            }
        }
        for (RicConfig deletedConfig : this.ricConfigs.values()) {
            modifications.add(new RicConfigUpdate(deletedConfig, RicConfigUpdate.Type.REMOVED));
        }
        this.ricConfigs = newRicConfigs;

        return Flux.fromIterable(modifications);
    }

    public boolean isS3Enabled() {
        return !(Strings.isNullOrEmpty(s3EndpointOverride) || Strings.isNullOrEmpty(s3Bucket));
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }
}
