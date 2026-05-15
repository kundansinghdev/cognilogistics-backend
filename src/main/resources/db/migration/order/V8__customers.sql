-- =============================================================================
-- Migration:  V8__customers.sql        (NEW BASELINE — schema.sql v5.0 alignment)
-- Module:     order
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the three normalised customer tables (DD-CUST-02):
--     1. customers           — identity + activation state
--     2. customer_addresses  — many-per-customer (BILLING / SHIPPING / BOTH)
--     3. customer_contacts   — many-per-customer (PRIMARY / FINANCE / LOGISTICS)
--
-- DD-CUST-02 RATIONALE:
--   Older drafts of the customers table had address fields embedded directly
--   (address_line_1, city, state, …). v5.0 split them into a separate table because:
--     - Real customers have multiple addresses (billing vs shipping vs warehouse).
--     - Real customers have multiple contact people (primary, finance, logistics).
--     - One-to-many relationships require a separate table for normal-form integrity.
--
-- SHADOW CUSTOMER PATTERN (BR-ORD-04):
--   When a TP user creates an order for a phone we don't yet have, we INSERT a
--   placeholder customer row with `is_shadow=TRUE`. The row has no addresses or
--   contacts attached yet — those get added when the customer is "activated" (the
--   TP edits their profile to fill in real details).
--
-- ⚠️ Replaces legacy V8__customers.sql (BIGINT-based). Fresh DB required.
-- =============================================================================


-- ── 1. customers ─────────────────────────────────────────────────────────────
CREATE TABLE customers (

    id                   CHAR(36)        NOT NULL,

    -- The legal / registered company name. Used on invoices, GR, LR documents.
    -- Required (NOT NULL) per v5.0 — the application supplies a placeholder
    -- ("Unknown Customer") for shadow customers if the TP didn't enter a name.
    legal_name           VARCHAR(255)    NOT NULL,

    -- Optional friendlier brand / trade name. May differ from legal_name
    -- (e.g. legal: "Reliance Industries Ltd", trade: "Reliance").
    trade_name           VARCHAR(255)    NULL,

    -- Customer phone — globally unique. The canonical identity for portal login
    -- and for the BR-ORD-04 shadow-customer match.
    phone                VARCHAR(15)     NOT NULL,

    -- 15-character Indian GSTIN. NULL when no_gst=TRUE (BR-ORD-12).
    -- Validated against the standard regex at the service layer.
    gstin                VARCHAR(15)     NULL,

    -- TRUE = customer is not GST-registered. When TRUE, gstin must be NULL.
    no_gst               BOOLEAN         NOT NULL DEFAULT FALSE,

    -- TRUE = placeholder created by the TP during order entry (BR-ORD-04).
    -- The customer hasn't activated portal access. UI shows a "shadow" badge.
    -- Flipped to FALSE when the customer completes OTP+PIN portal activation.
    is_shadow            BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Onboarding wizard step for the customer's portal account.
    --   1 = phone known (shadow row exists)
    --   2 = OTP verified
    --   3 = profile complete — full portal access
    -- Surfaced to the customer-portal front-end after login.
    onboarding_step      TINYINT         NOT NULL DEFAULT 1,

    -- Tenancy of the row: which TP account first created this customer row.
    -- Required for tenant scoping — a customer "belongs to" the TP that
    -- registered them. Cross-TP customer sharing is out of scope for v5.0.
    created_by_tp_id     CHAR(36)        NULL,

    -- The user (typically TP_ADMIN or TP_TRANSPORT_MANAGER) who entered the row.
    -- Audit-only; deletion of the user does NOT cascade to customer.
    created_by_user_id   CHAR(36)        NULL,

    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Phone uniqueness — the basis of every customer-lookup-by-phone path.
    UNIQUE KEY uq_customer_phone (phone),

    -- Tenant-scoped lookups: "show me all customers for THIS TP" is the
    -- dominant read pattern. Indexed for fast filtering.
    INDEX idx_customer_tp (created_by_tp_id),

    -- Optional FK to tp_accounts. ON DELETE SET NULL — preserve customer audit
    -- history even if their original TP is deleted.
    CONSTRAINT fk_customer_tp
        FOREIGN KEY (created_by_tp_id) REFERENCES tp_accounts(id) ON DELETE SET NULL,

    -- Optional FK to users. SET NULL on user deletion; we want the row to survive.
    CONSTRAINT fk_customer_creator_user
        FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Customer identity + activation state. DD-CUST-02. v5.0 schema.';


-- ── 2. customer_addresses ────────────────────────────────────────────────────
-- Many addresses per customer. address_type discriminates BILLING vs SHIPPING.
-- A customer typically has ONE billing address and ONE shipping address, but the
-- schema allows N of each so a customer with multiple shipping warehouses works.
CREATE TABLE customer_addresses (

    id              CHAR(36)        NOT NULL,
    customer_id     CHAR(36)        NOT NULL,

    -- BILLING / SHIPPING / BOTH. Application enforces exactly-one-default-per-type
    -- via the (customer_id, address_type, is_default) read-side check.
    address_type    VARCHAR(20)     NOT NULL DEFAULT 'BILLING',

    address_line_1  VARCHAR(255)    NOT NULL,
    address_line_2  VARCHAR(255)    NULL,
    city            VARCHAR(100)    NOT NULL,
    state           VARCHAR(100)    NOT NULL,
    pincode         VARCHAR(10)     NULL,

    -- TRUE for the customer's preferred address of this type. Used by order
    -- creation to auto-fill pickup/drop fields when the user picks a customer.
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Hot path: "give me all addresses for this customer" — fired on order create.
    INDEX idx_ca_customer (customer_id),

    -- Cascade so deleting a customer wipes their addresses (no orphans).
    CONSTRAINT fk_ca_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Customer addresses. Many-per-customer. DD-CUST-02.';


-- ── 3. customer_contacts ─────────────────────────────────────────────────────
-- Many contact people per customer. PRIMARY / FINANCE / LOGISTICS.
CREATE TABLE customer_contacts (

    id              CHAR(36)        NOT NULL,
    customer_id     CHAR(36)        NOT NULL,

    -- PRIMARY / FINANCE / LOGISTICS. Drives notification routing
    -- (e.g. order status updates → PRIMARY; invoices → FINANCE).
    contact_type    VARCHAR(20)     NOT NULL DEFAULT 'PRIMARY',

    contact_name    VARCHAR(255)    NOT NULL,

    -- At least one of phone or email is expected; both are nullable so partial
    -- contact info doesn't block customer creation.
    phone           VARCHAR(15)     NULL,
    email           VARCHAR(255)    NULL,

    -- TRUE for the customer's main day-to-day contact. Application convention:
    -- exactly one row per customer should have is_primary=TRUE; the service
    -- maintains that invariant on update.
    is_primary      BOOLEAN         NOT NULL DEFAULT FALSE,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_cc_customer (customer_id),

    CONSTRAINT fk_cc_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Customer contact people. Many-per-customer. DD-CUST-02.';
