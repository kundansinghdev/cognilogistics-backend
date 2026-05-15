-- =============================================================================
-- Migration:  V6__offices.sql            (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     user
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `offices` table — branch locations of a TP account.
--
--   Every order is assigned to exactly one office (`orders.office_id`). Branch
--   staff (TP_TRANSPORT_MANAGER role) are scoped to specific offices via
--   `user_office_assignments` (created in V7) and can only act on orders for
--   their assigned offices. TP_ADMIN bypasses the office-membership check.
--
-- BUSINESS RULES (CLAUDE.md §8):
--   BR-OFF-01  name, code, city, state are mandatory on POST /offices
--   BR-OFF-02  code must be unique within tp_account_id
--   BR-OFF-03  gstin (if provided) must match the 15-char Indian GSTIN regex
--   BR-OFF-04  is_active=false is the soft-delete (DD-08) — orders preserve their
--              office_id reference; the office just disappears from dropdowns
--   BR-OFF-05  POST/PATCH/DELETE require role=TP_ADMIN
--   BR-OFF-06  is_active=false blocked when non-DELIVERED/non-CANCELLED orders exist
--   BR-OFF-07  code is normalised to uppercase before persist
--
-- CHANGES FROM LEGACY V6:
--   - Switched id and tp_account_id to CHAR(36) UUIDs (v5.0).
--   - Added: code (mandatory), address, gstin, is_active.
--   - city/state are now NOT NULL (BR-OFF-01).
--   - REMOVED: is_primary column. v5.0 does not model a "primary office" —
--     the concept of a designated head office is folded into ordering / display
--     logic in the application layer. The legacy column is gone entirely.
--   - Added UNIQUE (tp_account_id, code) for BR-OFF-02.
--   - Added composite index (tp_account_id, is_active) for the active-only dropdown
--     query (DD-08). The dropdown is loaded on every order-creation screen, so this
--     index is on the hot path.
--   - FK ON DELETE RESTRICT — once an office is referenced by orders we don't allow
--     it to be deleted; the soft-delete via is_active is the supported path.
-- =============================================================================

CREATE TABLE offices (

    -- CHAR(36) UUID. Generated server-side in OfficeService.create.
    id              CHAR(36)        NOT NULL,

    -- Owning TP account. Tenant scope — every office query MUST include
    -- tp_account_id in WHERE (BR-MT-01).
    tp_account_id   CHAR(36)        NOT NULL,

    -- Display name (e.g. "Faridabad Hub 1"). Required (BR-OFF-01).
    name            VARCHAR(255)    NOT NULL,

    -- Short mnemonic code (e.g. "FB1"). Unique within the TP account (BR-OFF-02).
    -- Auto-uppercased by the service before INSERT (BR-OFF-07).
    code            VARCHAR(10)     NOT NULL,

    -- Required. Stored as VARCHAR (not enum) so adding a new city doesn't require
    -- a migration (BR-OFF-01 design decision).
    city            VARCHAR(100)    NOT NULL,

    -- Required. Same VARCHAR-not-enum reasoning as city.
    state           VARCHAR(100)    NOT NULL,

    -- Indian PIN code. Held as VARCHAR(10) — third-party APIs sometimes deliver
    -- PIN codes with separators (e.g. "121-001"); we want flexibility.
    pincode         VARCHAR(10)     NULL,

    -- Free-text street address. Optional — used on GR / LR documents and shipping labels.
    address         VARCHAR(500)    NULL,

    -- Optional branch-level GSTIN (15 chars). Indian branch-level GST registration is valid;
    -- an office GSTIN may legitimately differ from the parent TP account's master GSTIN.
    -- Validated against the GSTIN regex in service code if non-null (BR-OFF-03).
    gstin           VARCHAR(15)     NULL,

    -- Soft-delete flag (DD-08). FALSE = deactivated.
    --   - Hidden from order-assignment dropdowns (`/offices/dropdown`).
    --   - Cannot be flipped to FALSE while non-terminal orders are still assigned (BR-OFF-06).
    --   - Existing orders keep their office_id reference; the audit history is preserved.
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- BR-OFF-02: code unique per TP account.
    UNIQUE KEY uq_office_tp_code (tp_account_id, code),

    -- Tenant-scoped lookup. Almost every read path filters by tp_account_id.
    INDEX idx_office_tp           (tp_account_id),

    -- Composite index for the dropdown (`WHERE tp_account_id = ? AND is_active = TRUE`).
    -- DD-08 — this query runs on every order-creation screen, so it's worth its own index.
    INDEX idx_office_active       (tp_account_id, is_active),

    -- ON DELETE RESTRICT: an office that has been referenced by orders or assignments
    -- cannot be hard-deleted; the deactivation flow must be used instead.
    CONSTRAINT fk_office_tp
        FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Branch offices per TP account. Soft-delete via is_active. v5.0 schema.';
