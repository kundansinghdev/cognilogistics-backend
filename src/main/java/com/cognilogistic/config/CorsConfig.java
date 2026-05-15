package com.cognilogistic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for browser-based front-end clients.
 *
 * <p>The Spring Security {@code SecurityConfig} reads this bean instead of
 * disabling CORS, so preflight ({@code OPTIONS}) requests get a proper
 * {@code Access-Control-Allow-Origin} response and the browser doesn't block
 * the actual call with a "Network Error" / opaque CORS failure.
 *
 * <p><strong>Allowed origins are env-driven</strong> via
 * {@code cors.allowed-origins} (comma-separated). Defaults cover the local
 * Vite dev server on port 5173 plus a couple of common alternatives. Add the
 * deployed front-end origin via the env var or {@code application.yml}
 * override at deploy time — never wildcard {@code *} when
 * {@code allowCredentials=true} (the spec forbids it).
 *
 * <p>Why {@code allowCredentials=true} even though the API uses bearer JWTs
 * (not cookies): the front-end's {@code apiClient} may set
 * {@code withCredentials} for forward-compat, and {@code Authorization}
 * headers are technically credentials in the CORS spec — exposing them on
 * preflight requires this flag.
 */
@Configuration
public class CorsConfig {

    /** Default origins when the env var is unset — covers local Vite + Vue/Next defaults. */
    private static final String DEFAULT_ORIGINS =
            "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000";

    /** Methods the API accepts. {@code OPTIONS} is included so the browser preflight succeeds. */
    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");

    /**
     * Headers the front-end sends. {@code Authorization} is the JWT bearer header;
     * {@code Content-Type} drives the JSON body; the rest are conventional.
     */
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin");

    /** Headers the front-end is allowed to read off responses (rare but useful for downloads). */
    private static final List<String> EXPOSED_HEADERS =
            List.of("Authorization", "Content-Disposition");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:" + DEFAULT_ORIGINS + "}") String originsCsv) {

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        cfg.setAllowedMethods(ALLOWED_METHODS);
        cfg.setAllowedHeaders(ALLOWED_HEADERS);
        cfg.setExposedHeaders(EXPOSED_HEADERS);
        cfg.setAllowCredentials(true);
        // 1-hour preflight cache: the browser doesn't re-issue OPTIONS for every
        // call within this window. Reduces network chatter and dev-tools noise.
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        // Actuator probes are public; the FE health-check pings them too. Apply the
        // same CORS shape so they don't get blocked.
        source.registerCorsConfiguration("/actuator/**", cfg);
        return source;
    }
}
