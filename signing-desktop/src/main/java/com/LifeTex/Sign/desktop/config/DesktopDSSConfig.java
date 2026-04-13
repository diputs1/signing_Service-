package com.lifetex.sign.desktop.config;

import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DesktopDSSConfig {

    @Bean
    public CertificateVerifier certificateVerifier() {
        // For desktop app, we start with a simple verifier.
        // In the future, we can add offline CRL/OCSP checking or trusted stores if
        // needed.
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        return verifier;
    }
}
