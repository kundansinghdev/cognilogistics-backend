package com.cognilogistic.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Paths that are {@code permitAll} in {@link SecurityConfig}. Shared with
 * {@link JwtAuthFilter} so stale Bearer tokens on public routes do not block pre-auth flows.
 */
@Component
public class PublicApiRequestMatcher implements RequestMatcher {

    private final RequestMatcher delegate = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/auth/check-phone"),
            new AntPathRequestMatcher("/api/v1/auth/send-otp"),
            new AntPathRequestMatcher("/api/v1/auth/verify-otp"),
            new AntPathRequestMatcher("/api/v1/auth/setup-pin"),
            new AntPathRequestMatcher("/api/v1/auth/login"),
            new AntPathRequestMatcher("/api/v1/auth/refresh"),
            new AntPathRequestMatcher("/api/v1/auth/reset-pin/**"),
            new AntPathRequestMatcher("/api/v1/portal/auth/send-otp"),
            new AntPathRequestMatcher("/api/v1/portal/auth/verify-otp"),
            new AntPathRequestMatcher("/api/v1/customer/auth/send-otp"),
            new AntPathRequestMatcher("/api/v1/customer/auth/verify-otp"),
            new AntPathRequestMatcher("/api/v1/legal/**"),
            new AntPathRequestMatcher("/actuator/health"),
            new AntPathRequestMatcher("/actuator/info"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-ui.html"),
            new AntPathRequestMatcher("/swagger-ui/**"));

    @Override
    public boolean matches(HttpServletRequest request) {
        return delegate.matches(request);
    }
}
