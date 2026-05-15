package com.cognilogistic.config;

import com.cognilogistic.auth.service.AuthProperties;
import com.cognilogistic.platform.api.ApiError;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the CogniLogistic backend.
 *
 * <p>Configures a stateless, JWT-based security model:
 * <ul>
 *   <li>CSRF and CORS are disabled (JWT provides CSRF protection; CORS is handled by a
 *       separate gateway or the deployment infrastructure).</li>
 *   <li>Sessions are never created (STATELESS policy).</li>
 *   <li>Public endpoints (auth flows, portal OTP, Actuator health, Swagger UI) are
 *       permit-all; all other requests require a valid JWT.</li>
 *   <li>{@link JwtAuthFilter} is inserted before the standard username-password filter.</li>
 *   <li>Custom JSON error responses are written for unauthenticated and access-denied cases.</li>
 * </ul>
 *
 * <p>Also enables the {@link AuthProperties} configuration properties binding via
 * {@code @EnableConfigurationProperties}.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Configures the main security filter chain.
     *
     * @param http      the Spring Security HTTP builder
     * @param jwtFilter the JWT validation filter injected by Spring
     * @param mapper    Jackson mapper for writing JSON error responses
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtFilter,
                                           ObjectMapper mapper) throws Exception {
        http
                // CSRF is safe to disable here because authentication is stateless JWT —
                // there is no session cookie that an attacker's site could ride on, so the
                // CSRF token mechanism would be defending nothing.
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is enabled and driven by the CorsConfigurationSource bean
                // (see com.cognilogistic.config.CorsConfig) — defaults allow the
                // local Vite dev origin http://localhost:5173. Without this,
                // browser preflight returns no Access-Control-Allow-Origin and
                // the FE sees an opaque "Network Error".
                .cors(c -> {})
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/api/v1/auth/check-phone",
                                "/api/v1/auth/send-otp",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/setup-pin",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/reset-pin/**",
                                "/api/v1/portal/auth/send-otp",
                                "/api/v1/portal/auth/verify-otp",
                                // /customer/auth/* aliases for the same controller (BACKEND_GAPS §8 path rename).
                                "/api/v1/customer/auth/send-otp",
                                "/api/v1/customer/auth/verify-otp",
                                // FE reads current Terms / Privacy versions on signup-screen mount,
                                // pre-auth, so this read endpoint is public (BACKEND_SPEC_TC_CONSENT.md §4.2).
                                "/api/v1/legal/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, authEx) -> {
                            res.setStatus(ErrorCode.UNAUTHORIZED.status().value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiResponse<Void> body = ApiResponse.fail(
                                    ApiError.of(ErrorCode.UNAUTHORIZED, "Authentication required"));
                            res.getWriter().write(mapper.writeValueAsString(body));
                        })
                        .accessDeniedHandler((req, res, denyEx) -> {
                            res.setStatus(ErrorCode.FORBIDDEN.status().value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiResponse<Void> body = ApiResponse.fail(
                                    ApiError.of(ErrorCode.FORBIDDEN, "Access denied"));
                            res.getWriter().write(mapper.writeValueAsString(body));
                        })
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
