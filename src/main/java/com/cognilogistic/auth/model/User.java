package com.cognilogistic.auth.model;

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

import java.util.UUID;

/**
 * JPA entity for the {@code users} table — the universal identity record.
 *
 * <p><strong>Phone is the canonical login key (DD-01).</strong> Every authenticated
 * identity in the platform — TP staff, Logistics Partners, Customers, Platform Admins —
 * has exactly one row here, keyed on phone.
 *
 * <p>Login modalities (see auth.md §1):
 * <ul>
 *   <li><strong>PIN-based</strong> — TP_ADMIN, TP_TRANSPORT_MANAGER, PARTNER_TP, and
 *       provisioned COGNILOGISTIC_ADMIN. Each has a row in {@code auth_credentials}.</li>
 *   <li><strong>OTP-only</strong> — CUSTOMER portal users (no PIN row).</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 57–77.
 * The id is a CHAR(36) UUID stored as Java {@link String} — see {@link #id} for why.
 *
 * <p><strong>Deferred FKs:</strong> {@link #tpAccountId} and {@link #partnerTpProfileId}
 * are nullable. The FK constraints to {@code tp_accounts} and {@code partner_tp_profiles}
 * are added by the user-module migration once those tables exist (resolves the circular
 * reference between users → tp_accounts → users).
 *
 * <p><strong>Onboarding:</strong> {@link #onboardingStep} drives the front-end's first-login
 * flow. Values: 1 = phone+PIN set; 2 = profile (name + WhatsApp) provided; 3 = complete.
 * COGNILOGISTIC_ADMIN treats this as always 3 (no onboarding wizard for platform admins).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /**
     * Primary key — UUID stored as CHAR(36).
     *
     * <p>We use Java {@link String} (not {@link java.util.UUID}) at the entity level so
     * the stored format is unambiguous to any reader and there's no Hibernate UUID-to-VARCHAR
     * conversion magic to debug. The id is generated server-side when a new User is created
     * (typically in {@link com.cognilogistic.auth.service.AuthService}, but also seeded via
     * {@link #newPrimary} for new TP signups) — see {@link #ensureId()}.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The user's phone number — primary login identifier. E.164 format expected
     * (e.g. {@code +919876543210}). Globally unique across the entire platform — UNIQUE
     * constraint at the DB level. {@code VARCHAR(15)}.
     */
    @Column(name = "phone", nullable = false, unique = true, length = 15)
    private String phone;

    /**
     * Optional email — metadata only. NEVER used as a login key (DD-01: phone-only auth).
     * Used in support tooling and for displaying user details in the Admin Portal.
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * Display name. Set during onboarding step 2 (or seeded at insert time for direct-DB-insert
     * COGNILOGISTIC_ADMIN users). NULL until the user provides it.
     *
     * <p>Renamed from the legacy {@code full_name} column; v5.0 schema uses {@code name}.
     */
    @Column(name = "name", length = 255)
    private String name;

    /**
     * The user's role — drives every authorisation decision downstream. See {@link UserRole}.
     * Stored as the enum's {@code name()} string in a VARCHAR(40) column so future role names
     * (longer than the current 22-char max) don't require a schema change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private UserRole role;

    /**
     * Legacy shadow flag. In v5.0 the canonical "shadow customer" concept lives on
     * {@code customers.is_shadow}; this column on {@code users} is kept at FALSE for
     * normal accounts and exists for migration compatibility. New code should NOT set this
     * to TRUE — create a Customer row instead.
     *
     * <p>BR-AUTH-11: shadow accounts cannot log in via the auth flow.
     */
    @Column(name = "is_shadow", nullable = false)
    private boolean shadow;

    /**
     * Tenant scope for TP-side users (TP_ADMIN, TP_TRANSPORT_MANAGER). NULL for
     * PARTNER_TP, CUSTOMER, and COGNILOGISTIC_ADMIN.
     *
     * <p>FK constraint added by the user-module migration once {@code tp_accounts} exists.
     */
    @Column(name = "tp_account_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /**
     * Partner-TP profile linkage. Set when {@link #role} is {@code PARTNER_TP}; NULL otherwise.
     *
     * <p>FK constraint added by the user-module migration once {@code partner_tp_profiles} exists.
     */
    @Column(name = "partner_tp_profile_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String partnerTpProfileId;

    /**
     * Customer linkage. Set when {@link #role} is {@code CUSTOMER} once OTP onboarding
     * completes; NULL for every other role and for shadow customers that never log in.
     *
     * <p>Schema reference: {@code users.customer_id} added by migration
     * {@code V20260508001__users_customer_id.sql}. FK to {@code customers.id} with
     * {@code ON DELETE SET NULL}.
     *
     * <p>Why an explicit FK rather than discovering the link via phone uniqueness:
     * the customer portal scopes "my orders" by this id; a direct FK is enforceable,
     * cheap to JOIN, and survives phone-number changes. Mirrors the existing
     * {@link #tpAccountId} and {@link #partnerTpProfileId} pattern.
     */
    @Column(name = "customer_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String customerId;

    /**
     * Onboarding wizard progress (1 → 2 → 3). See class-level Javadoc for value meanings.
     * Surfaced to the front-end via {@code LoginResponse.onboardingStep}.
     *
     * <p>Modeled as {@link Integer} for null-safety even though the schema column is
     * {@code TINYINT NOT NULL}. {@code @JdbcTypeCode(SqlTypes.TINYINT)} pins the JDBC
     * type so Hibernate {@code ddl-auto: validate} accepts the wider Java type against
     * the narrower DB column without forcing callers to switch to {@code Byte}.
     * Defaulted to 1 in {@link #newPrimary}.
     */
    @Column(name = "onboarding_step", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer onboardingStep = 1;

    /**
     * WhatsApp contact number. May differ from {@link #phone} (some users prefer a separate
     * WhatsApp line). Used for order-status notifications via the WhatsApp template channel.
     * Set during onboarding step 2.
     */
    @Column(name = "whatsapp_number", length = 15)
    private String whatsappNumber;

    /**
     * Generates and assigns a new UUID to {@link #id} if one isn't already set.
     * Called by services before persisting a freshly-built User to avoid every caller having
     * to remember to do it.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    /**
     * Factory: constructs a new TP_ADMIN user during the OTP-verified first-login flow
     * (see auth.md §3.1). The user is given a fresh UUID; {@code tpAccountId} stays NULL —
     * the TP account is created in {@code AuthService.setupPin} once the user picks their PIN.
     *
     * @param phone the verified phone number
     * @return unsaved User entity ready to {@code save()}
     */
    public static User newPrimary(String phone) {
        User u = new User();
        u.ensureId();
        u.phone = phone;
        // The first user to sign up for a TP becomes its TP_ADMIN (the account owner).
        // BR-OFF-05 / BR-ORD-05 / BR-IMP-05 all gate on this role.
        u.role = UserRole.TP_ADMIN;
        u.shadow = false;
        u.onboardingStep = 1;
        return u;
    }

    // NOTE: the legacy `newShadow(...)` factory was removed in PR A3 along with the
    // SHADOW role enum value. The shadow-customer concept now lives on the customers
    // table as `customers.is_shadow=TRUE`; create a Customer (via Customer.newShadow)
    // instead of constructing a placeholder User. See order.md §3.2 (BR-ORD-04).
}
