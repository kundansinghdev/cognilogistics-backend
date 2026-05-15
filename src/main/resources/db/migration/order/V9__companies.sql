-- =============================================================================
-- Migration:  V9__companies.sql        (NEW BASELINE — schema.sql v5.0 alignment)
-- Module:     order
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `companies` table — the per-TP Company Master GSTIN registry
--   (BR-ORD-12).
--
-- BR-ORD-12: GSTIN AUTO-FILL ON ORDER CREATION:
--   When a TP user enters a 15-character GSTIN at order creation, the front-end
--   does an inline lookup to this table:
--     SELECT legal_name, primary_contact_*, address_*
--       FROM companies
--      WHERE tp_account_id = ? AND gstin = ?
--   If found, the customer name and contact fields are auto-populated. If not
--   found, the user is offered an "Add to Company Master" CTA which inserts a row
--   here for next time.
--
-- KEY DESIGN DECISIONS:
--   - Tenant-scoped: same GSTIN may appear under different TPs because each TP
--     curates their own list. The UNIQUE on (tp_account_id, gstin) enforces
--     "no duplicate GSTIN within one TP."
--   - Soft-delete via is_active. Hard-delete is blocked by FK to orders if any
--     order has ever referenced this company (added in V10).
--
-- ⚠️ Replaces legacy V20260506006__company_master.sql. Fresh DB required.
-- =============================================================================

CREATE TABLE companies (

    id                        CHAR(36)        NOT NULL,

    -- Tenant scope. Each TP curates their own company master list (BR-MT-01).
    tp_account_id             CHAR(36)        NOT NULL,

    -- Registered legal name. Used for invoice / GR / LR auto-fill.
    legal_name                VARCHAR(255)    NOT NULL,

    -- Optional brand / trade name for display (e.g. "Reliance" rather than
    -- "Reliance Industries Ltd").
    trade_name                VARCHAR(255)    NULL,

    -- 15-character GSTIN. NULL when no_gst=TRUE. The lookup key for BR-ORD-12.
    -- Validated in the service against the standard regex.
    gstin                     VARCHAR(15)     NULL,

    -- TRUE = company is not GST registered. When TRUE, gstin must be NULL.
    no_gst                    BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Postal address. Used to auto-fill order fields and on shipping documents.
    address_line_1            VARCHAR(255)    NULL,
    address_line_2            VARCHAR(255)    NULL,
    city                      VARCHAR(100)    NULL,
    state                     VARCHAR(100)    NULL,
    pincode                   VARCHAR(10)     NULL,

    -- Primary contact at the company. Single row here, not a list — Company
    -- Master is a quick-lookup cache, not a full CRM. The richer multi-contact
    -- model lives on `customer_contacts` (DD-CUST-02). When a customer is created
    -- from a Company Master entry, the primary contact here seeds the customer's
    -- PRIMARY contact row.
    primary_contact_name      VARCHAR(255)    NULL,
    primary_contact_phone     VARCHAR(15)     NULL,
    primary_contact_email     VARCHAR(255)    NULL,

    -- Free-text internal notes (e.g. "Always call on Mondays before 11am").
    notes                     VARCHAR(500)    NULL,

    -- Soft-delete flag. Inactive companies don't appear in the GSTIN lookup
    -- dropdown but historical data is preserved.
    is_active                 BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit: which user added this company to the master.
    created_by_user_id        CHAR(36)        NOT NULL,

    created_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- BR-ORD-12: same GSTIN can't appear twice in one TP's company master.
    -- (Across TPs is fine — each TP has their own curated list.)
    UNIQUE KEY uq_company_tp_gstin (tp_account_id, gstin),

    -- Lookup by company name within a TP. Used by the search-as-you-type
    -- autocomplete on the order-creation customer field.
    INDEX idx_company_tp_name   (tp_account_id, legal_name),

    -- Active-only listing for the dropdown.
    INDEX idx_company_tp_active (tp_account_id, is_active),

    -- Tenancy FK. RESTRICT — never cascade tenant deletion through customers /
    -- companies; deleting a TP requires explicit cleanup of dependent data.
    CONSTRAINT fk_company_tp
        FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE RESTRICT,

    -- Audit FK. RESTRICT — keep the audit trail intact.
    CONSTRAINT fk_company_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-TP GSTIN-keyed customer registry. BR-ORD-12. v5.0 schema.';
