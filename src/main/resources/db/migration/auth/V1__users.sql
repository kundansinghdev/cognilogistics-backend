-- =============================================================================
-- Migration:  V1__users.sql        (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     auth
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the universal `users` identity table — the root of every authenticated
--   identity in the platform. Phone is the canonical login key (DD-01); email is
--   metadata-only with no email-based login path anywhere in the system.
--
-- ROLES recognised (users.role enum string):
--   TP_ADMIN              — owner of a Transport Provider account (PIN-modality login)
--   TP_TRANSPORT_MANAGER  — TP staff scoped to specific offices (PIN)
--   PARTNER_TP            — Logistics Partner / sub-contractor (PIN)
--   CUSTOMER              — sender / shipper using the customer portal (OTP-only, no PIN)
--   COGNILOGISTIC_ADMIN   — CogniLogistic platform staff (OTP-only, no PIN)
-- See auth.md §1 for the full role + login-modality matrix.
--
-- ONBOARDING_STEP values (TP-side users only; ADMIN treats as always 3):
--   1 = phone + PIN set         — login capable, banner shows "Welcome!"
--   2 = name + WhatsApp set     — profile partially filled
--   3 = profile complete        — sidebar shows org name + plan badge
-- See auth.md §3.6 for the profile-completion flow.
--
-- DEFERRED FKs:
--   `tp_account_id` and `partner_tp_profile_id` are nullable CHAR(36) columns here —
--   the foreign-key constraints are added later by the user-module migration once
--   `tp_accounts` and `partner_tp_profiles` exist. This pattern handles the circular
--   reference between users ↔ tp_accounts (a TP account references its primary user,
--   and users reference their TP account).
--
-- ⚠️ MIGRATION REPLACES LEGACY V1 (BIGINT-based). Fresh DB required — Flyway will fail
--    checksum validation on a DB where the old V1 was previously applied.
-- =============================================================================

CREATE TABLE users (

    -- Server-generated UUID. CHAR(36) for uniformity across the v5.0 schema.
    -- Generated in code via UUID.randomUUID().toString().
    id                     CHAR(36)        NOT NULL,

    -- Primary identity. E.164 format expected (+CCXXXXXXXXXX). Globally unique.
    -- Schema column is VARCHAR(15) — fits +91 + 10 digits + 2 reserve chars.
    -- DD-01: phone is the canonical login key — no email-based login anywhere.
    phone                  VARCHAR(15)     NOT NULL,

    -- Optional metadata for support / billing. NEVER used as a login key.
    email                  VARCHAR(255)    NULL,

    -- Display name. Set during onboarding step 2 (or by the COGNILOGISTIC_ADMIN
    -- direct-insert seed for platform-admin users). NULL until the user provides it.
    name                   VARCHAR(255)    NULL,

    -- Role enum string. See header comment for the 5 valid values.
    -- Stored as VARCHAR(40) to leave room for future role names.
    role                   VARCHAR(40)     NOT NULL,

    -- TRUE for placeholder customer rows historically — but in v5.0 the shadow flag
    -- has moved to `customers.is_shadow`. Kept here at FALSE for the rare legacy case
    -- where a phone collision creates a placeholder users row.
    -- See order.md §3.2 for the BR-ORD-04 shadow customer rule.
    is_shadow              BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Tenant scope for TP-side users. NULL for PARTNER_TP / CUSTOMER / COGNILOGISTIC_ADMIN.
    -- FK constraint added by the user-module migration once `tp_accounts` exists.
    tp_account_id          CHAR(36)        NULL,

    -- Partner-TP profile linkage. Set when role = PARTNER_TP. NULL otherwise.
    -- FK constraint added by the user-module migration once `partner_tp_profiles` exists.
    partner_tp_profile_id  CHAR(36)        NULL,

    -- Onboarding wizard progress (TP-side only). See header comment for values.
    -- Surfaced to the front-end via LoginResponse.onboardingStep.
    onboarding_step        TINYINT         NOT NULL DEFAULT 1,

    -- WhatsApp contact number, may differ from `phone`. Set during onboarding step 2.
    -- Used for order-status notifications via the WhatsApp template channel.
    -- V20260507005 in the canonical migration set.
    whatsapp_number        VARCHAR(15)     NULL,

    -- Audit timestamps (server-side; populated by JPA auditing or DB defaults).
    created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Phone uniqueness enforces "one user per phone" globally — the basis of every
    -- auth flow's "find user by phone" lookup.
    UNIQUE KEY uq_users_phone (phone),

    -- Common lookup: support / admin search by email.
    INDEX idx_users_email      (email),

    -- Role-filtered listing (e.g. Admin Portal "list COGNILOGISTIC_ADMIN users").
    INDEX idx_users_role       (role),

    -- Tenant-scoped listing — the dominant query for TP staff lists.
    INDEX idx_users_tp_account (tp_account_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Universal identity table. Phone is canonical. v5.0 schema.';
