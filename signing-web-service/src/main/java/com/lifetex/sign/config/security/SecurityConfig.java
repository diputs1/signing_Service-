package com.lifetex.sign.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String SERVICE_ID_HEADER = "X-Service-Id";

    private static final String INTERNAL_SERVICE_ID = "SERVER_TAN_CANG";

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final JwtDecoder ssoJwtDecoder;
    private final JwtDecoder internalJwtDecoder;
    private final TrustedServicesProperties trustedServicesProperties;

    public SecurityConfig(
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            JwtDecoder ssoJwtDecoder,
            JwtDecoder internalJwtDecoder,
            TrustedServicesProperties trustedServicesProperties) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.ssoJwtDecoder = ssoJwtDecoder;
        this.internalJwtDecoder = internalJwtDecoder;
        this.trustedServicesProperties = trustedServicesProperties;
    }

    /**
     * Filter chain cho các API public (không cần xác thực)
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicApiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        // "/api/sign/demo/document",
                        // "/api/sign/demo/document-with-image",
                        // "/api/sign/demo/document-initial-signature",
                        // "/api/sign/demo/document-file-url",
                        "/api/sign/demo/cert-chain/**"
                // "/api/sign-remote/demo/document"
                // "/api/sign/document-initial-signature",
                // "/api/sign/document-with-image"
                )
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .cors(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Filter chain cho các API private (cần xác thực)
     * Tự động phân biệt SSO token vs Internal JWT dựa vào header X-Service-Id
     */
    @Bean
    @Order(2)
    public SecurityFilterChain privateApiFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .bearerTokenResolver(new OptionalBearerTokenResolver())
                        .authenticationManagerResolver(authenticationManagerResolver()));

        return http.build();
    }

    /**
     * AuthenticationManagerResolver để phân biệt loại token
     * Dựa vào header X-Service-Id để quyết định dùng decoder nào
     */
    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
        return request -> {
            String serviceId = request.getHeader(SERVICE_ID_HEADER);

            // System.out.println("\n" + "=".repeat(80));
            // System.out.println("AuthenticationManagerResolver - Luợt request mới:");
            // System.out.println("=".repeat(80));
            // System.out.println("Request URI: " + request.getRequestURI());
            // System.out.println("X-Service-Id header: [" + serviceId + "]");
            // System.out.println("X-Service-Id is null? " + (serviceId == null));
            // System.out.println("X-Service-Id is empty? " + (serviceId != null &&
            // serviceId.isEmpty()));

            Map<String, TrustedServicesProperties.ServiceConfig> trustedServices = trustedServicesProperties
                    .getTrustedServices();

            // System.out.println("Trusted services in config: " +
            // trustedServices.keySet());
            // System.out.println("Trusted services size: " + trustedServices.size());

            // Debug từng service
            // trustedServices.forEach((key, value) -> {
            // System.out.println(" - Service Key: [" + key + "], Name: " +
            // value.getName());
            // });

            // Kiểm tra xem service-id có tồn tại trong trusted-services không
            if (serviceId != null && !serviceId.trim().isEmpty()) {
                String trimmedServiceId = serviceId.trim();
                boolean exists = trustedServices.containsKey(trimmedServiceId);

                // System.out.println("Checking if [" + trimmedServiceId + "] exists in
                // trusted-services: " + exists);

                if (exists) {
                    // System.out.println(" MATCH! Using INTERNAL JWT Decoder for service: " +
                    // trimmedServiceId);
                    // System.out.println("=".repeat(80) + "\n");
                    JwtAuthenticationProvider internalProvider = new JwtAuthenticationProvider(internalJwtDecoder);
                    return new ProviderManager(internalProvider);
                } else {
                    // System.out.println("NO MATCH! Service [" + trimmedServiceId + "] not found in
                    // trusted-services");
                }
            }

            // System.out.println("Using WSO2 SSO JWT Decoder (default)");
            // System.out.println("=".repeat(80) + "\n");

            // Ngược lại -> dùng SSO JWT Decoder (OAuth2)
            JwtAuthenticationProvider ssoProvider = new JwtAuthenticationProvider(ssoJwtDecoder);
            return new ProviderManager(ssoProvider);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}