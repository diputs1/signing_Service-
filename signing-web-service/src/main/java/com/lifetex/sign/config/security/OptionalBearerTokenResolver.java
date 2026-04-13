package com.lifetex.sign.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class OptionalBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        try {
            return delegate.resolve(request); // có token thì trả về
        } catch (Exception ex) {
            return null; // token lỗi → coi như không có
        }
    }
}
