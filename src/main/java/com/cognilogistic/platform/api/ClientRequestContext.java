package com.cognilogistic.platform.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tiny helper for extracting the client's IP address and User-Agent from an
 * {@link HttpServletRequest} in a way that's safe behind a proxy.
 *
 * <p>Used by audit / consent flows that must record the originating client.
 * Spec reference: BACKEND_SPEC_TC_CONSENT.md §5.3.
 *
 * <p><strong>Trusted-proxy contract:</strong> in production the deployment
 * topology is (Cloudflare → Azure App Gateway → app), and we trust those
 * upstreams to set {@code X-Forwarded-For} correctly. Without that trust
 * (e.g. dev / direct access), reading {@code X-Forwarded-For} blindly is a
 * spoofing vector — a hostile client just sets the header to whatever it
 * wants.
 *
 * <p><strong>UAT compromise:</strong> the production trusted-proxy CIDR
 * list isn't yet finalised. For now we accept {@code X-Forwarded-For} when
 * it's present (so cloud-fronted requests log the real client IP) and fall
 * back to {@code request.getRemoteAddr()} otherwise. Document this caveat in
 * the deployment runbook before pilot. The first-hop IP from
 * {@code X-Forwarded-For} (leftmost in the comma-separated list) is what
 * we use — that's the originating client when the proxy chain is honest.
 */
public final class ClientRequestContext {

    /** Standard de-facto header set by HTTP-aware reverse proxies (CloudFront, ALB, nginx, Cloudflare). */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Some Azure / IIS chains use {@code X-Real-IP} instead of (or in addition to) X-Forwarded-For. */
    private static final String X_REAL_IP = "X-Real-IP";

    private ClientRequestContext() {
        // utility class
    }

    /**
     * Returns the best-effort client IP for the request.
     *
     * <p>Order of preference:
     * <ol>
     *   <li>Leftmost entry of {@code X-Forwarded-For} (first hop = originating client)</li>
     *   <li>{@code X-Real-IP}</li>
     *   <li>{@link HttpServletRequest#getRemoteAddr()} (direct-connect peer)</li>
     * </ol>
     *
     * <p>Returns {@code null} only if all of the above are missing — extremely rare;
     * mostly a defensive fallback for unit tests with mocked requests.
     *
     * @param request the inbound request, may be {@code null} (returns {@code null})
     * @return the resolved IP, or {@code null} if nothing is available
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For is a comma-separated list; the leftmost entry is the
            // originating client (subsequent entries are intermediate proxies).
            int comma = xff.indexOf(',');
            return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader(X_REAL_IP);
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Returns the {@code User-Agent} header verbatim, or {@code null} if absent.
     * No truncation — the consent table column is {@code TEXT}.
     *
     * @param request the inbound request, may be {@code null}
     * @return the raw User-Agent header value, or {@code null}
     */
    public static String resolveUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        return ua == null || ua.isBlank() ? null : ua;
    }
}
