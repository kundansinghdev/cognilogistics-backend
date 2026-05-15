-- =============================================================================
-- Migration:  V5__tp_accounts.sql        (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     user
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `tp_accounts` table — the top-level tenant unit for a Transport
--   Provider (logistics company) using the platform. Every business row in the
--   system (orders, tenders, offices, customers, vehicles…) is scoped by
--   `tp_account_id` to enforce multi-tenant isolation (BR-MT-01).
--
-- ALSO PERFORMS DEFERRED FK SETUP:
--   1. `users.tp_account_id` → `tp_accounts.id`  (deferred from V1 because of the
--      circular dependency: users existed first, now tp_accounts can be referenced).
--   2. `tp_accounts.primary_user_id` → `users.id`  (deferred so we can add it AFTER
--      tp_accounts exists). ON DELETE SET NULL — orphaning the TP if its primary
--      user is deleted is preferable to cascading the deletion across all the TP's
--      data.
--
-- ACCOUNT_STATUS WORKFLOW (BR-PLN-02, see admin.md §3.2):
--   PENDING  — new TP signup, blocked from business actions
--   APPROVED — Platform Admin reviewed, full access per plan
--   REJECTED — Platform Admin declined, login allowed but business actions return 403
--
-- PLAN TIERS (BR-PLN-04):
--   BASIC      — Tender module only, capped at 5 tenders / month
--   PREMIUM    — Tender + Order + Branch + Company + Fleet (unlimited)
--   ENTERPRISE — All of PREMIUM + Reports module
--
--   Plan is set ONLY by COGNILOGISTIC_ADMIN via the Admin Portal — TP users
--   cannot self-upgrade. plan_set_at / plan_set_by record the audit trail.
--
-- ⚠️ MIGRATION REPLACES LEGACY V5 (BIGINT-based). Fresh DB required.
-- =============================================================================

CREATE TABLE tp_accounts (

    -- Server-generated UUID. Created in TpAccountRepositoryImpl.createForPrimaryUser
    -- via UUID.randomUUID().toString() during the auth setup-pin flow.
    id                          CHAR(36)        NOT NULL,

    -- Organisation display name. Set during onboarding step 2 (the profile-completion
    -- flow — see auth.md §3.6). NOT NULL in v5.0; the application enforces "non-empty"
    -- before saving via Bean Validation on the request DTO. Initial signup creation
    -- in TpAccountRepositoryImpl.createForPrimaryUser supplies a placeholder which the
    -- onboarding wizard replaces.
    name                        VARCHAR(255)    NOT NULL,

    -- 15-character Indian GSTIN. Validated in code against the standard regex
    -- `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`.
    -- NULL when no_gst = TRUE (BR-ORD-12).
    gstin                       VARCHAR(15)     NULL,

    -- TRUE = TP is not GST registered. When TRUE, gstin must be NULL.
    -- Used by order creation to suppress GST fields on invoices / GR / LR docs.
    no_gst                      BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Postal address. All optional — set during onboarding step 2.
    address_line_1              VARCHAR(255)    NULL,
    address_line_2              VARCHAR(255)    NULL,
    city                        VARCHAR(100)    NULL,
    state                       VARCHAR(100)    NULL,
    pincode                     VARCHAR(10)     NULL,

    -- The TP_ADMIN user who owns this account. Nullable because of the deferred-FK
    -- pattern: tp_accounts and users are created in separate transactions during
    -- signup (auth setup-pin flow stamps this column AFTER both rows exist).
    primary_user_id             CHAR(36)        NULL,

    -- Plan tier. Default BASIC for new signups. Only COGNILOGISTIC_ADMIN can change.
    -- BR-PLN-04. Comment on the column itself in MySQL is intentionally rich so
    -- DBAs running `SHOW CREATE TABLE` see the rules without consulting docs.
    plan                        VARCHAR(20)     NOT NULL DEFAULT 'BASIC'
                                COMMENT 'BASIC=Tender only (5/mo), PREMIUM=Tender+Order+Branch+Company+Fleet, ENTERPRISE=all+Reports. Set by COGNILOGISTIC_ADMIN only.',

    -- Audit trail for plan changes.
    plan_set_at                 DATETIME        NULL,
    plan_set_by                 CHAR(36)        NULL
                                COMMENT 'FK users.id of the COGNILOGISTIC_ADMIN who set the plan',

    -- TRUE = TP owns physical trucks/trailers (carrier).
    -- FALSE = TP is a broker/aggregator that arranges transport via Partner TPs.
    -- Drives tender broadcast logic post-UAT (DD-NET-01).
    fleet_owner                 BOOLEAN         NOT NULL DEFAULT FALSE
                                COMMENT 'DD-NET-01: TRUE = owns trucks. FALSE = broker/aggregator.',

    -- Approval workflow. New signups start PENDING; a Platform Admin reviews and
    -- moves to APPROVED or REJECTED via the Admin Portal. PENDING blocks Order /
    -- Branch / Company access even on plan=PREMIUM (BR-PLN-02).
    account_status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING|APPROVED|REJECTED. New signups queue for COGNILOGISTIC_ADMIN review.',

    -- Audit trail for status changes.
    account_status_updated_at   DATETIME        NULL,
    account_status_updated_by   CHAR(36)        NULL
                                COMMENT 'FK users.id of the COGNILOGISTIC_ADMIN who changed status',

    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_tp_name           (name),
    INDEX idx_tp_gstin          (gstin),
    INDEX idx_tp_plan           (plan),
    INDEX idx_tp_account_status (account_status),
    INDEX idx_tp_fleet_owner    (fleet_owner)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Transport Provider tenant root. Multi-tenant scope. v5.0 schema.';


-- =============================================================================
-- DEFERRED FOREIGN KEY: users.tp_account_id → tp_accounts.id
--   Originally defined as a NULLABLE column in V1 because tp_accounts didn't exist
--   yet. Now that it does, attach the constraint. ON DELETE SET NULL: deleting the
--   TP detaches users from the deleted tenant rather than cascading the deletion.
-- =============================================================================
ALTER TABLE users
    ADD CONSTRAINT fk_users_tp
        FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE SET NULL;


-- =============================================================================
-- DEFERRED FOREIGN KEY: tp_accounts.primary_user_id → users.id
--   Same circular-reference pattern. ON DELETE SET NULL — orphaning the TP if its
--   primary user is deleted is preferable to cascading.
-- =============================================================================
ALTER TABLE tp_accounts
    ADD CONSTRAINT fk_tp_primary_user
        FOREIGN KEY (primary_user_id) REFERENCES users(id) ON DELETE SET NULL;
