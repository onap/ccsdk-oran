/*-
 * ========================LICENSE_START=================================
 * ONAP : ccsdk oran
 * ======================================================================
 * Copyright (C) 2019-2022 Nordix Foundation. All rights reserved.
 * Modifications Copyright (C) 2025 OpenInfra Foundation Europe.
 * All rights reserved.
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

package org.onap.ccsdk.oran.a1policymanagementservice.clients;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;

import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig;
import org.onap.ccsdk.oran.a1policymanagementservice.configuration.WebClientConfig.HttpProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

/**
 * Factory for a generic reactive REST client.
 */
public class AsyncRestClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SslContextFactory sslContextFactory;
    private final HttpProxyConfig httpProxyConfig;
    private final SecurityContext securityContext;

    public AsyncRestClientFactory(WebClientConfig clientConfig, SecurityContext securityContext) {
        if (clientConfig != null) {
            if (clientConfig.isSslEnabled()) {
                this.sslContextFactory = new CachingSslContextFactory(clientConfig);
            } else {
                this.sslContextFactory = null;
                logger.debug("SSL is turned OFF for the web client");
            }
            this.httpProxyConfig = clientConfig.getHttpProxyConfig();
        } else {
            logger.warn("No configuration for web client defined, HTTPS will not work");
            this.sslContextFactory = null;
            this.httpProxyConfig = null;
        }
        this.securityContext = securityContext;
    }

    public AsyncRestClient createRestClientNoHttpProxy(String baseUrl) {
        return createRestClient(baseUrl, false);
    }

    public AsyncRestClient createRestClientUseHttpProxy(String baseUrl) {
        return createRestClient(baseUrl, true);
    }

    private AsyncRestClient createRestClient(String baseUrl, boolean useHttpProxy) {
        if (this.sslContextFactory != null) {
            try {
                return new AsyncRestClient(baseUrl, this.sslContextFactory.createSslContext(),
                        useHttpProxy ? httpProxyConfig : null, this.securityContext);
            } catch (Exception e) {
                String exceptionString = e.toString();
                logger.error("Could not init SSL context, reason: {}", exceptionString);
            }
        }
        return new AsyncRestClient(baseUrl, null, httpProxyConfig, this.securityContext);
    }

    private class SslContextFactory {
        private final WebClientConfig clientConfig;

        public SslContextFactory(WebClientConfig clientConfig) {
            this.clientConfig = clientConfig;
        }

        public SslContext createSslContext() throws UnrecoverableKeyException, NoSuchAlgorithmException,
                CertificateException, KeyStoreException, IOException {
            return this.createSslContext(createKeyManager());
        }

        private SslContext createSslContext(KeyManagerFactory keyManager)
                throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
            if (this.clientConfig.isTrustStoreUsed()) {
                return createSslContextRejectingUntrustedPeers(this.clientConfig.getTrustStore(),
                        this.clientConfig.getTrustStorePassword(), keyManager);
            } else {
                // Trust anyone
                return SslContextBuilder.forClient() //
                        .keyManager(keyManager) //
                        .trustManager(InsecureTrustManagerFactory.INSTANCE) //
                        .build();
            }
        }

        private SslContext createSslContextRejectingUntrustedPeers(String trustStorePath, String trustStorePass,
                KeyManagerFactory keyManager)
                throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {

            final KeyStore trustStore = getTrustStore(trustStorePath, trustStorePass);
            List<Certificate> certificateList = Collections.list(trustStore.aliases()).stream() //
                    .filter(alias -> isCertificateEntry(trustStore, alias)) //
                    .map(alias -> getCertificate(trustStore, alias)) //
                    .collect(Collectors.toList());
            final X509Certificate[] certificates = certificateList.toArray(new X509Certificate[certificateList.size()]);

            return SslContextBuilder.forClient() //
                    .keyManager(keyManager) //
                    .trustManager(certificates) //
                    .build();
        }

        private boolean isCertificateEntry(KeyStore trustStore, String alias) {
            try {
                return trustStore.isCertificateEntry(alias);
            } catch (KeyStoreException e) {
                logger.error("Error reading truststore {}", e.getMessage());
                return false;
            }
        }

        private Certificate getCertificate(KeyStore trustStore, String alias) {
            try {
                return trustStore.getCertificate(alias);
            } catch (KeyStoreException e) {
                logger.error("Error reading truststore {}", e.getMessage());
                return null;
            }
        }

        private KeyManagerFactory createKeyManager() throws NoSuchAlgorithmException, CertificateException, IOException,
                UnrecoverableKeyException, KeyStoreException {
            final KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            final KeyStore keyStore = KeyStore.getInstance(this.clientConfig.getKeyStoreType());
            final String keyStoreFile = this.clientConfig.getKeyStore();
            final String keyStorePassword = this.clientConfig.getKeyStorePassword();
            final String keyPassword = this.clientConfig.getKeyPassword();
            try (final InputStream inputStream = new FileInputStream(keyStoreFile)) {
                keyStore.load(inputStream, keyStorePassword.toCharArray());
            }
            keyManager.init(keyStore, keyPassword.toCharArray());
            return keyManager;
        }

        private synchronized KeyStore getTrustStore(String trustStorePath, String trustStorePass)
                throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {

            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(new FileInputStream(ResourceUtils.getFile(trustStorePath)), trustStorePass.toCharArray());
            return store;
        }
    }

    public class CachingSslContextFactory extends SslContextFactory {
        private SslContext cachedContext = null;

        public CachingSslContextFactory(WebClientConfig clientConfig) {
            super(clientConfig);
        }

        @Override
        public SslContext createSslContext() throws UnrecoverableKeyException, NoSuchAlgorithmException,
                CertificateException, KeyStoreException, IOException {
            if (this.cachedContext == null) {
                this.cachedContext = super.createSslContext();
            }
            return this.cachedContext;

        }
    }
}
