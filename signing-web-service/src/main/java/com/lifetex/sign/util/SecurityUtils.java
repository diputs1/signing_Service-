package com.lifetex.sign.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Utility class để lấy thông tin user từ SecurityContext
 * Sử dụng static methods, không cần inject
 */
public class SecurityUtils {

    private SecurityUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Lấy JWT token hiện tại
     */
    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }

        return Optional.empty();
    }

    /**
     * Lấy username của user hiện tại
     *
     * @return username hoặc "anonymous" nếu không authenticated
     */
    public static String getCurrentUsername() {
        return getCurrentJwt()
                .map(SecurityUtils::extractUsername)
                .orElse("anonymous");
    }

    /**
     * Lấy email của user hiện tại
     *
     * @return email hoặc null nếu không có
     */
    public static String getCurrentUserEmail() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("email"))
                .orElse(null);
    }

    /**
     * Lấy user ID (subject) của user hiện tại
     *
     * @return user ID hoặc null
     */
    public static String getCurrentUserId() {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString("sub"))
                .orElse(null);
    }

    /**
     * Lấy full name của user
     *
     * @return full name hoặc null
     */
    public static String getCurrentUserFullName() {
        return getCurrentJwt()
                .map(jwt -> {
                    String name = jwt.getClaimAsString("name");
                    if (name != null)
                        return name;

                    String givenName = jwt.getClaimAsString("given_name");
                    String familyName = jwt.getClaimAsString("family_name");

                    if (givenName != null) {
                        return givenName + (familyName != null ? " " + familyName : "");
                    }

                    return null;
                })
                .orElse(null);
    }

    /**
     * Lấy giá trị của một claim cụ thể
     *
     * @param claimName tên của claim
     * @return giá trị claim hoặc null
     */
    public static String getClaim(String claimName) {
        return getCurrentJwt()
                .map(jwt -> jwt.getClaimAsString(claimName))
                .orElse(null);
    }

    /**
     * Kiểm tra token có phải từ Internal system không
     *
     * @return true nếu là internal token
     */
    public static boolean isInternalToken() {
        return getCurrentJwt()
                .map(jwt -> {
                    String issuer = jwt.getClaimAsString("iss");
                    return "tan-cang-issuer".equals(issuer);
                })
                .orElse(false);
    }

    /**
     * Kiểm tra token có phải từ WSO2 SSO không
     *
     * @return true nếu là WSO2 token
     */
    public static boolean isWso2Token() {
        return getCurrentJwt()
                .map(jwt -> {
                    String issuer = jwt.getClaimAsString("iss");
                    return issuer != null && issuer.contains("wso2");
                })
                .orElse(false);
    }

    /**
     * Lấy issuer của token
     *
     * @return issuer hoặc null
     */
    public static String getIssuer() {
        return getClaim("iss");
    }

    /**
     * Lấy service ID (cho internal token)
     *
     * @return service ID hoặc null
     */
    public static String getServiceId() {
        return getClaim("serviceId");
    }

    /**
     * Kiểm tra user có authenticated không
     *
     * @return true nếu authenticated
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Extract username từ JWT với fallback logic
     */
    private static String extractUsername(Jwt jwt) {
        // Thử các claim theo thứ tự ưu tiên
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null)
            return username;

        username = jwt.getClaimAsString("username");
        if (username != null)
            return username;

        username = jwt.getClaimAsString("email");
        if (username != null)
            return username;

        return jwt.getClaimAsString("sub");
    }

    /**
     * Lấy toàn bộ thông tin user dưới dạng object
     */
    public static UserContext getCurrentUserContext() {
        return getCurrentJwt()
                .map(jwt -> new UserContext(
                        jwt.getClaimAsString("sub"),
                        extractUsername(jwt),
                        jwt.getClaimAsString("email"),
                        getCurrentUserFullName(),
                        jwt.getClaimAsString("iss"),
                        jwt.getClaimAsString("serviceId")))
                .orElse(null);
    }

    /**
     * Simple DTO cho user context
     */
    public record UserContext(
            String userId,
            String username,
            String email,
            String fullName,
            String issuer,
            String serviceId) {
        public boolean isInternal() {
            return "tan-cang-issuer".equals(issuer);
        }

        public boolean isWso2() {
            return issuer != null && issuer.contains("wso2");
        }
    }
}