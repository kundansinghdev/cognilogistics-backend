package com.cognilogistic.user.model;

import com.cognilogistic.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code tp_accounts} table — the top-level tenant unit for a
 * Transport Provider (logistics company) using the platform.
 *
 * <p>All multi-tenant data in the system (orders, tenders, offices, customers,
 * vehicles, drivers, …) is scoped by {@link #id}. A TP account is created during the
 * auth setup-pin flow when a brand-new user finishes the OTP+PIN steps — see
 * {@link com.cognilogistic.user.repository.TpAccountRepositoryImpl#createForPrimaryUser}.
 *
 * <p><strong>Two cross-cutting state machines live on this row:</strong>
 * <ul>
 *   <li>{@link #accountStatus} — PENDING → APPROVED / REJECTED, gated by the
 *       Platform Admin (BR-PLN-02). Drives the {@code ACCOUNT_PENDING_APPROVAL} /
 *       {@code ACCOUNT_REJECTED} access gate that every business endpoint enforces.</li>
 *   <li>{@link #plan} — BASIC / PREMIUM / ENTERPRISE, set by the Platform Admin
 *       (BR-PLN-04). Drives the per-module access matrix via {@code plan_access_rules}.</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 132–161.
 *
 * <p><strong>Onboarding step lives elsewhere:</strong> v5.0 moved {@code onboarding_step}
 * from {@code tp_accounts} to {@code users}. {@link com.cognilogistic.auth.model.User#getOnboardingStep()}
 * is the canonical accessor; this entity has no onboarding-step field.
 */
@Entity
@Table(name = "tp_accounts")
@Getter
@Setter
@NoArgsConstructor
public class TpAccount extends BaseEntity {

    /**
     * Server-generated UUID. Populated in
     * {@link com.cognilogistic.user.repository.TpAccountRepositoryImpl#createForPrimaryUser}
     * via {@link #ensureId()}.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * Organisation display name (e.g. "Bhoomihaar Express"). Set during onboarding step 2
     * (the profile-completion modal — see auth.md §3.6). NOT NULL at the schema level;
     * the service supplies a placeholder ("Pending Setup") at signup creation and the
     * onboarding wizard replaces it before the user can advance past step 1.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * 15-character Indian GSTIN. Validated against the standard GSTIN regex by the
     * service before persist. NULL when {@link #noGst} is TRUE.
     */
    @Column(name = "gstin", length = 15)
    private String gstin;

    /**
     * TRUE = TP is not GST registered. When TRUE, {@link #gstin} must be NULL.
     * Used by order creation to suppress GST fields on invoices / GR / LR docs (BR-ORD-12).
     */
    @Column(name = "no_gst", nullable = false)
    private boolean noGst;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    /**
     * The TP_ADMIN user who owns this account. Nullable because the FK is added in a
     * deferred migration and tp_accounts is created in the same transaction as the
     * primary user — they reference each other circularly.
     *
     * <p>The auth setup-pin flow stamps this column AFTER both rows exist.
     */
    @Column(name = "primary_user_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String primaryUserId;

    /**
     * Commercial plan tier. Default {@link Plan#BASIC} for new signups. Only
     * COGNILOGISTIC_ADMIN can change (BR-PLN-04). The audit trail lives in
     * {@link #planSetAt} / {@link #planSetBy}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan = Plan.BASIC;

    /** Server timestamp of the last plan change. NULL for accounts that have never had a plan change. */
    @Column(name = "plan_set_at")
    private Instant planSetAt;

    /**
     * The COGNILOGISTIC_ADMIN's user id who last set the plan. CHAR(36) UUID.
     * NULL for accounts on the default BASIC plan that have never been touched.
     */
    @Column(name = "plan_set_by", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String planSetBy;

    /**
     * TRUE = this TP owns physical trucks/trailers (carrier).
     * FALSE = this TP is a broker/aggregator that arranges transport via Partner TPs.
     * Drives tender broadcast logic post-UAT (DD-NET-01).
     */
    @Column(name = "fleet_owner", nullable = false)
    private boolean fleetOwner;

    /**
     * Approval workflow state. Defaults to {@link AccountStatus#PENDING} for new signups.
     * BR-PLN-02 — PENDING / REJECTED block business-action endpoints.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    /** Server timestamp of the last account_status change. NULL until the first review. */
    @Column(name = "account_status_updated_at")
    private Instant accountStatusUpdatedAt;

    /**
     * The COGNILOGISTIC_ADMIN's user id who last changed the status. CHAR(36) UUID.
     * NULL for accounts that have never been reviewed (still PENDING).
     */
    @Column(name = "account_status_updated_by", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String accountStatusUpdatedBy;

    /**
     * Generates and assigns a UUID to {@link #id} if not already set. Convenience helper
     * so service code doesn't have to repeat the {@code UUID.randomUUID().toString()} call.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
