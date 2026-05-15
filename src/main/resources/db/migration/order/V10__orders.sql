-- =============================================================================
-- Migration:  V10__orders.sql        (NEW BASELINE — schema.sql v5.0 alignment)
-- Module:     order
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the two normalised order tables (DD-ORD-02):
--     1. orders          — hot table, scanned on every list / filter / dashboard query
--     2. order_details   — heavy fields (cargo, fleet, pricing) — 1:1, joined only on detail view
--
-- DD-ORD-02 RATIONALE:
--   The `orders` table is the read-hottest table in the system: dashboard counts,
--   "show me my pending orders", filter-by-status etc. all scan it. We want it
--   narrow so each row is small and fits more rows per page in the buffer pool.
--   Heavy text fields (notes, free-form descriptions) and rarely-needed fleet/
--   driver fields (only filled at FLEET_CONFIRMED, only shown on detail view)
--   live on `order_details`. Together they form one logical order; the JPA layer
--   may model this with @SecondaryTable or as separate @OneToOne entities.
--
-- ⚠️ NO ASSIGNED STATE (BR-ORD-07, DD-05):
--   The status enum has 6 values: CREATED / ACKNOWLEDGED / FLEET_CONFIRMED /
--   IN_TRANSIT / DELIVERED / CANCELLED. There is NO `ASSIGNED` value — office
--   assignment is an attribute change (setting `office_id`) that auto-advances
--   CREATED → ACKNOWLEDGED in the same transaction.
--
-- ⚠️ NO `FLEET_PENDING` STATUS (BR-ORD-07, DD-07):
--   The "Fleet Pending" dashboard tab is a query filter, not a stored status:
--     WHERE status = 'ACKNOWLEDGED' AND (order_type='PTL' OR vehicle_id IS NOT NULL)
--   Do NOT add it as an enum value or a column.
--
-- ⚠️ Replaces legacy V9__orders.sql. Fresh DB required.
-- =============================================================================


-- ── 1. orders (the hot table) ────────────────────────────────────────────────
CREATE TABLE orders (

    id                          CHAR(36)        NOT NULL,

    -- Tenant scope. Stamped server-side from the JWT (BR-MT-03) — never trust
    -- a client-supplied tp_account_id in the request body.
    tp_account_id               CHAR(36)        NOT NULL,

    -- Human-readable order number, format e.g. "COG-20260507-0001".
    -- Generated server-side; unique per TP. The UI shows this rather than the UUID.
    order_no                    VARCHAR(30)     NOT NULL,

    -- 6-state state machine. See class header for the lifecycle. Stored as
    -- the enum's name() string. NO ASSIGNED, NO FLEET_PENDING.
    status                      VARCHAR(30)     NOT NULL DEFAULT 'CREATED'
                                COMMENT 'CREATED|ACKNOWLEDGED|FLEET_CONFIRMED|IN_TRANSIT|DELIVERED|CANCELLED',

    -- FTL = Full Truck Load (one truck dedicated to this order).
    -- PTL = Part Truck Load (shared with other orders on the same vehicle/date — connected lot).
    order_type                  VARCHAR(10)     NOT NULL,

    -- NORMAL or EXPRESS delivery. EXPRESS may unlock priority routing post-UAT.
    delivery_type               VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',

    -- Branch office handling this order. NULL at CREATED (BR-ORD-05) — gets set
    -- during the auto-acknowledge transition. RESTRICT FK so an office with order
    -- history can't be hard-deleted (operational soft-delete via is_active instead).
    office_id                   CHAR(36)        NULL,

    -- Customer this order is for. Required at create time. RESTRICT FK so a
    -- customer with order history is never silently lost.
    customer_id                 CHAR(36)        NOT NULL,

    -- Optional Company Master link (BR-ORD-12). Set when the user picked a
    -- pre-existing company via GSTIN auto-lookup at order creation; NULL when
    -- the user typed customer details manually.
    company_id                  CHAR(36)        NULL,

    -- Pickup / drop free-text addresses. Stored as VARCHAR rather than FK to
    -- a separate addresses table because the same customer might ship from
    -- different one-off pickup locations (the customer's billing address isn't
    -- always the pickup point).
    pickup_location             VARCHAR(500)    NOT NULL,
    drop_location               VARCHAR(500)    NOT NULL,

    -- Required pickup date. drop_date is computed/expected — actual delivery is
    -- recorded in order_status_log when the order transitions to DELIVERED.
    pickup_date                 DATE            NOT NULL,
    expected_delivery_date      DATE            NULL,

    -- Goods type (e.g. "Steel coils", "Garments"). Free-text — keeping a type
    -- catalog is out of UAT scope.
    goods_type                  VARCHAR(100)    NOT NULL,

    -- Customer GSTIN snapshot at order time. Denormalised so historical orders
    -- show the GSTIN that applied at the time of order, even if the customer
    -- master record changes later.
    customer_gstin              VARCHAR(15)     NULL,

    -- Customer name snapshot at order time. Same denormalisation rationale —
    -- invoices / GR / LR refer to this name, not whatever the customer master
    -- says today.
    customer_name               VARCHAR(255)    NOT NULL,

    -- Free-text internal notes from TP staff. Visible to TP_ADMIN /
    -- TP_TRANSPORT_MANAGER, NOT to the customer-portal user.
    internal_notes              VARCHAR(1000)   NULL,

    -- Convenience flag for delivery_type='EXPRESS'. Denormalised so simple
    -- dashboard queries don't need to compute it. Application keeps both in sync.
    is_express                  BOOLEAN         NOT NULL DEFAULT FALSE,

    -- The user who created the order. Audit. RESTRICT FK so the row survives
    -- user deletion (we want the audit trail).
    created_by                  CHAR(36)        NOT NULL,

    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Order number is unique per TP (a TP doesn't issue duplicate order numbers).
    UNIQUE KEY uq_order_no (tp_account_id, order_no),

    -- Multi-tenant index — every list query filters by tp_account_id first.
    INDEX idx_order_tp_account  (tp_account_id),

    -- Status-filtered list within a TP — the dashboard's "pending"/"in transit"/
    -- "delivered" tabs all use this composite.
    INDEX idx_order_status      (tp_account_id, status),

    -- Office-scoped list (TP_TRANSPORT_MANAGER's daily view).
    INDEX idx_order_office      (office_id),

    -- Per-customer list (Customer Portal's "my orders" view).
    INDEX idx_order_customer    (customer_id),

    -- Per-company-master list (when user clicks a company in Company Master,
    -- shows their order history).
    INDEX idx_order_company     (company_id),

    -- Date-filtered list (e.g. "orders to pick up this week").
    INDEX idx_order_pickup_date (pickup_date),

    CONSTRAINT fk_order_tp
        FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_office
        FOREIGN KEY (office_id) REFERENCES offices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_company
        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_creator
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Hot table — narrow row, scanned on every list/filter. DD-ORD-02. v5.0.';


-- ── 2. order_details (1:1 with orders, heavy fields) ─────────────────────────
CREATE TABLE order_details (

    -- The order's id — IS the PK here. No surrogate. 1:1 join to orders.
    order_id                    CHAR(36)        NOT NULL,

    -- Cargo
    weight_kg                   DECIMAL(10,2)   NULL,
    volume_cbm                  DECIMAL(10,3)   NULL,
    requested_vehicle_type      VARCHAR(50)     NULL,

    -- Fleet / Driver — populated at FLEET_CONFIRMED (BR-ORD-09).
    -- assigned_vehicle_type may differ from requested_vehicle_type if the TP
    -- substitutes a different truck size.
    assigned_vehicle_type       VARCHAR(50)     NULL,

    -- Vehicle registration number. UPPERCASE (BR-ORD-09 / BR-ORD-11). Required
    -- for FTL at FLEET_CONFIRMED, optional for PTL.
    vehicle_number              VARCHAR(20)     NULL,

    -- Driver name — required for ALL orders at FLEET_CONFIRMED (BR-ORD-09).
    driver_name                 VARCHAR(255)    NULL,

    -- Driving licence number. UPPERCASE. Optional — Sarathi check is advisory
    -- only (BR-FLT-04).
    driver_dl                   VARCHAR(30)     NULL,

    -- Vahan / Sarathi advisory check results. NEVER block state-machine
    -- transitions (BR-FLT-04). Stored for audit + dashboard display.
    --   VERIFIED  — registry confirmed the data
    --   WARNING   — match found but with caveats (e.g. expired RC)
    --   NOT_FOUND — registry has no record
    vahan_status                VARCHAR(20)     NULL,
    sarathi_status              VARCHAR(20)     NULL,

    -- Pricing. Editable pre-FLEET_CONFIRMED (BR-ORD-08). Default 0 = "not yet set"
    -- so we can create orders without a price (negotiation in progress).
    price_inr                   INT             NOT NULL DEFAULT 0,

    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (order_id),

    -- Cascade delete with the parent order. order_details is meaningless without
    -- an order, so it follows the order's lifecycle automatically.
    CONSTRAINT fk_od_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Order heavy fields (cargo, fleet, pricing). 1:1 with orders. DD-ORD-02.';
