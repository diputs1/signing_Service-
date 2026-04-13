package com.lifetex.sign.config.security;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "security")
public class TrustedServicesProperties {

    private Map<String, ServiceConfig> trustedServices = new HashMap<>();

    /**
     * Configuration cho từng trusted service
     */
    @Data
    public static class ServiceConfig {
        private String name;
        private String type;
        private String issuer;
        private String secret;

    }
}