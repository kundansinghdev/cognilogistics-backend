package com.cognilogistic.auth.model;

/**
 * Authorisation role of a platform user. Stored as the enum's {@code name()} string in
 * the {@code users.role} column (VARCHAR(40)) and embedded as the {@code role} claim in
 * every JWT access token.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 line 62.
 *
 * <p>Spring Security grants the authority {@code ROLE_<name>} based on the enum value
 * (e.g. role {@link #TP_ADMIN} → {@code ROLE_TP_ADMIN}), enabling URL-level or
 * method-level security expressions such as {@code @PreAuthorize("hasRole('TP_ADMIN')")}.
 *
 * <p><strong>Login modalities</strong> (auth.md §1):
 * <ul>
 *   <li><strong>PIN-based</strong> — TP_ADMIN, TP_TRANSPORT_MANAGER, PARTNER_TP,
 *       and provisioned {@link #COGNILOGISTIC_ADMIN} users. Each has an
 *       {@code auth_credentials} row with a bcrypt-hashed PIN assigned at provisioning.</li>
 *   <li><strong>OTP-only</strong> — CUSTOMER portal users (no PIN row).</li>
 * </ul>
 *
 * <p><strong>Legacy enum values dropped in v5.0:</strong>
 * <ul>
 *   <li>{@code TP_PRIMARY} → renamed to {@link #TP_ADMIN}.</li>
 *   <li>{@code TP_BRANCH_USER} → renamed to {@link #TP_TRANSPORT_MANAGER}.</li>
 *   <li>{@code SHADOW} → no longer a user role. The shadow concept moved to a flag
 *       on the {@code customers} table ({@code customers.is_shadow}). Code that needs
 *       a placeholder customer creates a {@link com.cognilogistic.order.model.Customer}
 *       with {@code is_shadow=true} instead of a SHADOW user.</li>
 * </ul>
 */
public enum UserRole {

    /**
     * Owner / admin of a Transport Provider account. The first user to sign up for a
     * given TP becomes the TP_ADMIN. Manages branch offices, staff assignments, plan
     * info (read-only — only COGNILOGISTIC_ADMIN can change a TP's plan), and Partner TP
     * invitations.
     *
     * <p>BR-OFF-05: only TP_ADMIN may create / edit / deactivate offices.<br>
     * BR-ORD-05: only TP_ADMIN may reassign an order to a different office.
     *
     * <p>(Renamed from legacy {@code TP_PRIMARY}; the old enum value is no longer valid.)
     */
    TP_ADMIN,

    /**
     * Staff member of a TP account, scoped to one or more branch offices via
     * {@code user_office_assignments}. Day-to-day order operations: confirm fleet,
     * mark in-transit, deliver, cancel — but only on orders assigned to one of their
     * offices.
     *
     * <p>Cannot manage offices, staff, or partner network — those are TP_ADMIN-only.
     *
     * <p>(Renamed from legacy {@code TP_BRANCH_USER}.)
     */
    TP_TRANSPORT_MANAGER,

    /**
     * Sub-contractor / Logistics Partner that bids on tenders broadcast by primary TPs.
     * Has a corresponding row in {@code partner_tp_profiles} (linked via
     * {@link com.cognilogistic.auth.model.User#partnerTpProfileId}).
     *
     * <p>The Admin Portal v2.2 displays this role as "Logistics Partner" or "LP";
     * the schema enum stays {@code PARTNER_TP}. Was historically called "LSP" in
     * older documentation.
     */
    PARTNER_TP,

    /**
     * A customer / shipper who has been granted portal access by their TP. Authenticates
     * via OTP-only (no PIN). The JWT {@code sub} claim is the {@code customers.id} for
     * portal sessions; once the customer activates and a {@link com.cognilogistic.auth.model.User}
     * row is linked, the {@code sub} is the user id.
     */
    CUSTOMER,

    /**
     * CogniLogistic platform staff. Cross-tenant authority — can approve/reject TP
     * signups, set plan tiers, and impersonate any tenant via the Admin Portal "Log in as"
     * flow. Created by direct DB insert from the engineering team; no self-service signup,
     * registration, or PIN reset — login only via {@code POST /auth/login}.
     *
     * <p>(Replaces the legacy {@code ADMIN} value used in older drafts of the schema.
     * v5.0 disambiguates with the more explicit {@code COGNILOGISTIC_ADMIN}.)
     */
    COGNILOGISTIC_ADMIN
}
