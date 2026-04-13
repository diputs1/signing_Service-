package com.lifetex.sign.config;

import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DSSConfig {

    @Bean
    public CommonCertificateVerifier certificateVerifier() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        // Add CRL/OCSP sources here if needed
        return verifier;
    }
}
