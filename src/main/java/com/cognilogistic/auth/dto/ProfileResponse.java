package com.cognilogistic.auth.dto;

/**
 * Wrapper response for {@code GET /api/v1/auth/profile} and {@code PATCH /api/v1/auth/profile}.
 *
 * <p>The wire shape is {@code { "user": AuthUser }} (BACKEND_GAPS §1.1) — wrapped
 * inside the standard {@code ApiResponse} envelope:
 *
 * <pre>{@code
 * { "success": true, "data": { "user": { ... } } }
 * }</pre>
 *
 * <p>The wrapper exists (rather than returning {@link AuthUser} directly) to leave
 * room for adding sibling fields later (e.g. {@code tokens} on a future
 * profile-completes-and-rotates flow) without a breaking shape change.
 *
 * @param user the freshly-built identity payload reflecting the post-update state
 */
public record ProfileResponse(AuthUser user) {}
