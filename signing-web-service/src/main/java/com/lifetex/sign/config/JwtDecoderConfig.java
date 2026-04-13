package com.lifetex.sign.config;

import com.lifetex.sign.config.security.TrustedServicesProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.util.Map;

@Configuration
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String wso2JwkSetUri;

    private final TrustedServicesProperties trustedServicesProperties;

    public JwtDecoderConfig(TrustedServicesProperties trustedServicesProperties) {
        this.trustedServicesProperties = trustedServicesProperties;
    }

    /**
     * JwtDecoder cho WSO2 Identity Server (SSO) - Primary bean
     */
    @Bean("ssoJwtDecoder")
    public JwtDecoder ssoJwtDecoder() {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(wso2JwkSetUri)
                .jwtProcessorCustomizer(processor -> {
                    // WSO2 IS thường dùng typ = "at+jwt" cho access token
                    processor.setJWSTypeVerifier((header, context) -> {
                        // Bỏ qua việc verify typ để accept cả "at+jwt" và null
                    });
                })
                .build();

        // Thêm validator cho WSO2 token
        decoder.setJwtValidator(wso2JwtValidator());

        // Wrap với logging decorator
        return new LoggingJwtDecoderWrapper(decoder, "WSO2_SSO");
    }

    /**
     * JwtDecoder cho Internal JWT từ SERVER_TAN_CANG
     * Sử dụng HMAC SHA256 với secret key
     */
    @Bean("internalJwtDecoder")
    public JwtDecoder internalJwtDecoder() {

        Map<String, TrustedServicesProperties.ServiceConfig> services = trustedServicesProperties.getTrustedServices();

        TrustedServicesProperties.ServiceConfig tanCangConfig = services.get("SERVER_TAN_CANG");

        if (tanCangConfig == null) {
            System.err.println(" SERVER_TAN_CANG configuration NOT FOUND!");
            throw new IllegalStateException("SERVER_TAN_CANG configuration not found in trusted-services");
        }

        String secret = tanCangConfig.getSecret();
        String issuer = tanCangConfig.getIssuer();

        // Decode Base64 secret key
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");

        // Tạo JwtDecoder với HMAC SHA256
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Thêm validator cho internal token
        decoder.setJwtValidator(internalJwtValidator(issuer));

        // Wrap với logging decorator để log mỗi request
        return new LoggingJwtDecoderWrapper(decoder, "INTERNAL");
    }

    /**
     * Validator cho WSO2 token
     * - Validate default (exp, nbf, iss nếu config)
     * - Validate typ header (accept "at+jwt" hoặc null)
     */
    private OAuth2TokenValidator<Jwt> wso2JwtValidator() {
        // Default validator (kiểm tra exp, nbf, etc.)
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();

        // Custom validator cho typ header của WSO2
        OAuth2TokenValidator<Jwt> typValidator = jwt -> {
            String typ = jwt.getHeaders().get("typ") != null
                    ? jwt.getHeaders().get("typ").toString()
                    : null;

            // WSO2 IS dùng "at+jwt" cho access token, có thể null cho các trường hợp khác
            if (typ == null || "at+jwt".equalsIgnoreCase(typ) || "JWT".equalsIgnoreCase(typ)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            OAuth2ErrorCodes.INVALID_TOKEN,
                            "Invalid token type: " + typ + ". Expected: at+jwt or JWT",
                            null));
        };

        return new DelegatingOAuth2TokenValidator<>(defaultValidator, typValidator);
    }

    /**
     * Validator cho Internal JWT token
     * - Validate default (exp, nbf)
     * - Validate issuer
     */
    private OAuth2TokenValidator<Jwt> internalJwtValidator(String expectedIssuer) {
        // System.out.println("Creating internal JWT validator for issuer: " +
        // expectedIssuer);

        // Default validator
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();

        // Issuer validator
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(expectedIssuer);

        // Custom debug validator
        OAuth2TokenValidator<Jwt> debugValidator = jwt -> {
            // System.out.println(" Validating Internal JWT:");
            // System.out.println(" - Subject: " + jwt.getClaimAsString("sub"));
            // System.out.println(" - Issuer: " + jwt.getClaimAsString("iss"));
            // System.out.println(" - Issued At: " + jwt.getIssuedAt());
            // System.out.println(" - Expires At: " + jwt.getExpiresAt());
            // System.out.println(" - Service ID: " + jwt.getClaimAsString("serviceId"));
            return OAuth2TokenValidatorResult.success();
        };

        return new DelegatingOAuth2TokenValidator<>(defaultValidator, issuerValidator, debugValidator);
    }

    /**
     * Wrapper để log mỗi lần decode JWT
     * Đây là nơi bạn sẽ thấy log MỖI REQUESTd
     */
    private record LoggingJwtDecoderWrapper(JwtDecoder delegate, String decoderType) implements JwtDecoder {

        @Override
        public Jwt decode(String token) throws JwtException {
            // System.out.println("\n" + "#".repeat(40));
            // System.out.println("# DECODING JWT with " + decoderType + " Decoder");
            // System.out.println("#".repeat(40));

            try {
                long startTime = System.currentTimeMillis();

                // Actual decode
                Jwt jwt = delegate.decode(token);

                long endTime = System.currentTimeMillis();

                // System.out.println(" DECODE SUCCESS!");
                // System.out.println(" - Decoder Type: " + decoderType);
                // System.out.println(" - Algorithm: " + jwt.getHeaders().get("alg"));
                // System.out.println(" - Subject: " + jwt.getSubject());
                // System.out.println(" - Issuer: " + jwt.getIssuer());
                // System.out.println(" - Expires At: " + jwt.getExpiresAt());
                // System.out.println(" - Service ID: " + jwt.getClaimAsString("serviceId"));
                // System.out.println(" - Decode Time: " + (endTime - startTime) + "ms");
                // System.out.println("#".repeat(40) + "\n");

                return jwt;

            } catch (JwtException e) {
                System.err.println(" DECODE FAILED!");
                System.err.println("   - Decoder Type: " + decoderType);
                System.err.println("   - Error: " + e.getMessage());
                System.err.println("X".repeat(40) + "\n");
                throw e;
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}