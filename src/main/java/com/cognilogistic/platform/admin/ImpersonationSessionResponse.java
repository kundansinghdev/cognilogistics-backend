package com.cognilogistic.platform.admin;

import com.cognilogistic.auth.dto.AuthUser;
import com.cognilogistic.auth.dto.LoginResponse;

/**
 * Response body for {@code POST /api/v1/admin/impersonate} and
 * {@code POST /api/v1/admin/impersonate/{sessionId}/exit} (BACKEND_GAPS §7).
 *
 * <p>Carries:
 * <ul>
 *   <li>{@link #sessionId} — UUID the FE passes back to the {@code /exit} endpoint.</li>
 *   <li>{@link #login} — full {@link LoginResponse} for the new identity (target
 *       tenant on enter, admin on exit). Includes both tokens and the {@link AuthUser}
 *       payload so the FE can swap its identity store in one shot.</li>
 * </ul>
 *
 * @param sessionId  audit log row id (impersonation_audit_log.id)
 * @param login      tokens + identity payload — same shape as a regular login
 */
public record ImpersonationSessionResponse(String sessionId, LoginResponse login) {}
