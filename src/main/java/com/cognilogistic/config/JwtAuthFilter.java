package com.cognilogistic.config;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.service.TokenService;
import com.cognilogistic.platform.api.ApiError;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that validates the JWT Bearer token on every incoming HTTP request.
 *
 * <p>If a valid {@code Authorization: Bearer <token>} header is present, the filter
 * parses the JWT, extracts the identity claims, and populates the Spring Security
 * {@link org.springframework.security.core.context.SecurityContext} with an
 * {@link AuthPrincipal} wrapped in a {@link UsernamePasswordAuthenticationToken}.
 *
 * <p>If validation fails (expired, tampered, or missing token) the filter writes a JSON
 * 401 error response and short-circuits the filter chain — the request never reaches
 * the controller. On {@linkplain PublicApiRequestMatcher public} routes, an invalid
 * Bearer token is ignored so clients may send a stale token without breaking login/OTP.
 *
 * <p>Extends {@code OncePerRequestFilter} to guarantee the JWT is checked exactly once
 * per request, even in forwarded/included dispatcher scenarios.
 *
 * <p>This filter is registered before {@code UsernamePasswordAuthenticationFilter} (see
 * {@code SecurityConfig.filterChain}) because authentication is claim-based: the
 * identity comes from the Bearer JWT, not from a posted username/password form. Running
 * first lets the rest of the security chain see an already-authenticated principal.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final ObjectMapper mapper;
    private final PublicApiRequestMatcher publicPaths;

    public JwtAuthFilter(TokenService tokenService,
                         ObjectMapper mapper,
                         PublicApiRequestMatcher publicPaths) {
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.publicPaths = publicPaths;
    }

    /**
     * Intercepts every request, attempts to extract and validate a Bearer JWT, and
     * populates the security context if successful.
     *
     * @param req   the incoming HTTP request
     * @param res   the HTTP response; written to directly on 401 errors
     * @param chain the remainder of the filter chain
     * @throws ServletException if the filter chain throws
     * @throws IOException      if response writing fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Jws<Claims> jws = tokenService.parseAccessToken(token);
                Claims c = jws.getPayload();
                // SCHEMA: every id is a CHAR(36) UUID string. JWT.sub is a string by spec,
                // and our tp/ptp claims are also strings ("" sentinel for null — see TokenService).
                String userId = c.getSubject();
                String phone = c.get("phone", String.class);
                UserRole role = UserRole.valueOf(c.get("role", String.class));
                String tp = nullIfBlank(c.get("tp", String.class));
                String ptp = nullIfBlank(c.get("ptp", String.class));
                // imp claim: COGNILOGISTIC_ADMIN's user id when this token represents an
                // active impersonation session; null on every regular login token.
                // BACKEND_GAPS §7 — audit hooks branch on this to stamp impersonation
                // context on every mutating action.
                String imp = nullIfBlank(c.get("imp", String.class));

                AuthPrincipal principal = imp == null
                        ? new AuthPrincipal(userId, phone, role, tp, ptp)
                        : new AuthPrincipal(userId, phone, role, tp, ptp, true, imp);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                if (!publicPaths.matches(req)) {
                    writeError(res, ErrorCode.UNAUTHORIZED, "Invalid or expired access token");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * Treats both {@code null} and an empty/whitespace string as "no value" — matches the
     * "" sentinel TokenService writes when the user has no TP / PARTNER_TP linkage.
     * Keeps AuthPrincipal's nullability contract clean (null = absent, never empty string).
     */
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Writes a JSON-encoded API error response directly to the servlet response and sets
     * the HTTP status code matching the provided error code.
     *
     * @param res  the response to write to
     * @param code the error code (determines HTTP status)
     * @param msg  the human-readable error message
     * @throws IOException if serialization or response writing fails
     */
    private void writeError(HttpServletResponse res, ErrorCode code, String msg) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.fail(ApiError.of(code, msg));
        res.getWriter().write(mapper.writeValueAsString(body));
    }
}
