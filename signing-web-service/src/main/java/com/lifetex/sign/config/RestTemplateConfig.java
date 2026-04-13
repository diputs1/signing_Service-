package com.lifetex.sign.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${ejbca.client-cert.keystore-path}")
    private Resource keystoreResource;

    @Value("${ejbca.client-cert.keystore-password}")
    private String keystorePassword;

    @Value("${ejbca.client-cert.keystore-type:PKCS12}")
    private String keystoreType;

    @Value("${ejbca.ssl.trust-all:false}")
    private boolean trustAll;

    @Bean(name = "ejbcaRestTemplate")
    public RestTemplate ejbcaRestTemplate() {
        try {
            // log.info("Cau hinh RestTemplate cho EJBCA");
            //
            // log.info("=== Creating EJBCA RestTemplate ===");
            // log.info("Keystore path: {}", keystoreResource.getFilename());
            // log.info("Keystore exists: {}", keystoreResource.exists());

            // Load client keystore
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (InputStream keystoreStream = keystoreResource.getInputStream()) {
                keyStore.load(keystoreStream, keystorePassword.toCharArray());
            }

            // Build SSL Context
            SSLContextBuilder sslContextBuilder = SSLContextBuilder.create()
                    .loadKeyMaterial(keyStore, keystorePassword.toCharArray());

            if (trustAll) {
                log.warn("Cau hinh SSL de tin tuong tat ca cac chung chi");
                sslContextBuilder.loadTrustMaterial(null, (chain, authType) -> true);
            }

            SSLContext sslContext = sslContextBuilder.build();

            // Tao SSL Socket Factory
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    (hostname, session) -> trustAll // Hostname verifier
            );

            // Tao HttpClient voi SSL Socket Factory
            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            HttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            // Tao RestTemplate voi HttpClient
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(10000);
            factory.setConnectionRequestTimeout(10000);

            RestTemplate restTemplate = new RestTemplate(factory);

            log.info("EJBCA RestTemplate configured successfully");
            return restTemplate;

        } catch (Exception e) {
            log.error("Failed to configure EJBCA RestTemplate", e);
            throw new RuntimeException("Failed to configure SSL RestTemplate", e);
        }
    }
}