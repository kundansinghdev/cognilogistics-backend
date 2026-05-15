package com.cognilogistic.auth.security;

import com.cognilogistic.auth.model.UserRole;

/**
 * Immutable identity record carried in the Spring {@code SecurityContext} on every
 * authenticated request. Populated by {@link com.cognilogistic.config.JwtAuthFilter}
 * from the JWT claims, then injected into controller methods via
 * {@link CurrentUser}.
 *
 * <p>All ID fields are CHAR(36) UUID strings (v5.0 schema). Use {@link String} types
 * throughout downstream code that consumes this record.
 *
 * <p><strong>Roles:</strong> see {@link UserRole}. Two role categories matter for
 * authorisation:
 * <ul>
 *   <li>TP-side roles ({@link UserRole#TP_ADMIN}, {@link UserRole#TP_TRANSPORT_MANAGER}) —
 *       have a non-null {@link #tpAccountId}; queries should scope to this tenant.</li>
 *   <li>{@link UserRole#PARTNER_TP} — has a non-null {@link #partnerTpProfileId}.</li>
 *   <li>{@link UserRole#CUSTOMER} — typically has a non-null {@link #tpAccountId}
 *       pointing at the TP that granted them portal access (single-TP scoping).</li>
 *   <li>{@link UserRole#COGNILOGISTIC_ADMIN} — cross-tenant; both ID fields normally null
 *       unless the admin is in an active impersonation session ({@link #isImpersonated}).</li>
 * </ul>
 *
 * <p><strong>Impersonation:</strong> when a Platform Admin impersonates a tenant via the
 * Admin Portal "Log in as" flow, the JWT carries the impersonated tenant's ids in
 * {@link #userId} / {@link #tpAccountId}, with {@link #isImpersonated} = true and
 * {@link #impersonatedByUserId} pointing at the admin. Audit-aspect code reads these
 * fields to populate {@code audit_logs.impersonated_by_user_id} on every write.
 */
public record AuthPrincipal(

        /**
         * The authenticated user's id (CHAR(36) UUID). For impersonation sessions this is the
         * IMPERSONATED user, not the admin — see {@link #impersonatedByUserId}.
         */
        String userId,

        /**
         * The user's phone number. Carried for convenience so logs / audit messages
         * don't need to round-trip back to the database for the phone.
         */
        String phone,

        /**
         * The user's role. Drives every authorisation decision downstream.
         */
        UserRole role,

        /**
         * The TP account this user belongs to, or {@code null} for users not associated with a TP
         * (PARTNER_TP, COGNILOGISTIC_ADMIN outside of impersonation).
         */
        String tpAccountId,

        /**
         * The Partner TP profile id when {@link #role} is {@link UserRole#PARTNER_TP}; {@code null}
         * for every other role.
         */
        String partnerTpProfileId,

        /**
         * {@code true} when the request is part of an active admin-impersonation session.
         * Triggers the audit-aspect to stamp {@link #impersonatedByUserId} on every {@code audit_logs}
         * write. Plan-access checks during impersonation force the override to ENTERPRISE.
         */
        boolean isImpersonated,

        /**
         * The {@code COGNILOGISTIC_ADMIN}'s user id when {@link #isImpersonated} is true;
         * {@code null} otherwise.
         */
        String impersonatedByUserId

) {

    /**
     * Convenience constructor for the common (non-impersonation) case. Sets
     * {@link #isImpersonated} to {@code false} and {@link #impersonatedByUserId} to {@code null}.
     *
     * @param userId             the user id (UUID)
     * @param phone              the phone number
     * @param role               the user role
     * @param tpAccountId        the TP account id (or null)
     * @param partnerTpProfileId the Partner TP profile id (or null)
     */
    public AuthPrincipal(String userId, String phone, UserRole role,
                         String tpAccountId, String partnerTpProfileId) {
        this(userId, phone, role, tpAccountId, partnerTpProfileId, false, null);
    }

    /**
     * Returns {@code true} if the authenticated user is the TP account owner
     * (role {@link UserRole#TP_ADMIN}). Used to enforce TP_ADMIN-only operations such as:
     * <ul>
     *   <li>BR-OFF-05 — only TP_ADMIN may create / edit / deactivate offices.</li>
     *   <li>BR-ORD-05 — only TP_ADMIN may reassign an order's office.</li>
     * </ul>
     *
     * <p>Method name kept as {@code isPrimary()} (rather than {@code isAdmin()}) for
     * historical continuity — call sites remain readable, and "primary user" is still
     * the operational concept, just expressed via the new TP_ADMIN enum value.
     */
    public boolean isPrimary() {
        return role == UserRole.TP_ADMIN;
    }
}
