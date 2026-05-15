-- =============================================================================
-- Migration:  V14__tender_baseline.sql   (NEW BASELINE — schema.sql v5.0)
-- Module:     tender
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the four tender-module tables in one go. The tender domain is "TP
--   posts a request for transport, Logistics Partners bid, TP awards one bid,
--   winning LP confirms with vehicle + driver."
--
--     1. tenders           — the request: origin, destination, ref price, status
--     2. bids              — Logistics Partner offers on a tender
--     3. tp_assignments    — winning LP submits actual vehicle + driver after award
--     4. tender_order_refs — many-to-many link from tenders to PTL orders (BR-ORD-15)
--
-- TENDER STATE MACHINE (tender.md §3.1):
--     DRAFT → IN_PROGRESS → COMPLETED          (winner accepted)
--                        ↘ CANCELLED          (TP cancels)
--   No skipping; once COMPLETED or CANCELLED, terminal.
--
-- BID STATE MACHINE:
--     PENDING → ACCEPTED       (TP awarded)
--             → REJECTED       (TP rejected this specific bid, or TP awarded a sibling)
--             → WITHDRAWN      (LP voluntarily withdrew before TP decision)
--
-- BR-PLN-03 — BASIC PLAN TENDER CAP:
--   BASIC TPs can create at most 5 tenders per calendar month. Enforced at the
--   PUBLISH transition (DRAFT → IN_PROGRESS), not at draft-creation time, via
--   the `plan_usage` table (user module).
--
-- ⚠️ This module didn't have a Flyway folder before — V14 establishes the
--    baseline. Fresh DB required.
-- =============================================================================


-- ── 1. tenders ───────────────────────────────────────────────────────────────
-- The request-for-transport. Created by a TP, broadcast to Logistics Partners.
CREATE TABLE tenders (

    id              CHAR(36)        NOT NULL,

    -- Tenant scope. Stamped server-side from the JWT (BR-MT-04) — never trust a
    -- client-supplied tp_account_id.
    tp_account_id   CHAR(36)        NOT NULL,

    -- Human-readable tender number (e.g. "TND-20260507-0001"). Unique per TP.
    tender_number   VARCHAR(30)     NOT NULL,

    -- 4-state state machine. See file header.
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT'
                    COMMENT 'DRAFT|IN_PROGRESS|COMPLETED|CANCELLED',

    -- Optional human-friendly title (e.g. "Steel coils, Faridabad → Mumbai, 30 May").
    title           VARCHAR(255)    NULL,

    -- Free-text origin and destination. We don't normalise to lat/lng at v5.0;
    -- city / region strings are sufficient for tender broadcast filtering.
    origin          VARCHAR(255)    NULL,
    destination     VARCHAR(255)    NULL,

    -- Vehicle type the TP wants (e.g. "32ft container"). Free-text — bidders
    -- propose substitutions if they don't have an exact match.
    vehicle_type    VARCHAR(50)     NULL,

    pickup_date     DATE            NULL,
    delivery_date   DATE            NULL,

    -- Reference price the TP is willing to pay, in INR (whole rupees). Visible
    -- to bidders as guidance — bids may come in higher or lower. Default 0 = "open".
    ref_price_inr   INT             NULL DEFAULT 0,

    -- Free-text instructions / context for bidders.
    notes           VARCHAR(1000)   NULL,

    -- Audit: which TP user created the tender.
    created_by      CHAR(36)        NOT NULL,

    -- Set when status moves to COMPLETED — the partner_tp_profiles.id of the
    -- winning bidder. NULL until award.
    awarded_to      CHAR(36)        NULL,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_tender_no (tp_account_id, tender_number),
    INDEX idx_tender_tp     (tp_account_id),
    INDEX idx_tender_status (status),

    -- Tenancy and creator FKs. RESTRICT — preserve audit history.
    -- awarded_to FK to partner_tp_profiles is added in a later migration once
    -- that table exists (the user module's partner_tp_profiles is post-UAT).
    CONSTRAINT fk_tender_tp
        FOREIGN KEY (tp_account_id) REFERENCES tp_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tender_creator
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Transport requests broadcast to Logistics Partners. v5.0 schema.';


-- ── 2. bids ──────────────────────────────────────────────────────────────────
-- Logistics Partner offers on a tender. Sealed-envelope: each LP sees only their
-- own bid. The TP sees all bids and picks one.
CREATE TABLE bids (

    id            CHAR(36)        NOT NULL,
    tender_id     CHAR(36)        NOT NULL,

    -- The Logistics Partner placing the bid. References partner_tp_profiles.id —
    -- FK added when that table comes online (user module post-UAT).
    partner_id    CHAR(36)        NOT NULL,

    -- Offered price in INR (whole rupees).
    amount_inr    INT             NOT NULL,

    -- Estimated days from pickup to delivery. NULL if the LP doesn't commit.
    eta_days      INT             NULL,

    -- Free-text bid context (e.g. "Available May 10 onwards, returning empty").
    notes         VARCHAR(500)    NULL,

    -- 4-state bid state machine. See file header.
    status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                  COMMENT 'PENDING|ACCEPTED|REJECTED|WITHDRAWN',

    submitted_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- One bid per LP per tender. The application updates the existing row's
    -- status (PENDING → WITHDRAWN → re-PENDING via amount/notes change) rather
    -- than INSERT-ing a second row.
    UNIQUE KEY uq_bid_tender_partner (tender_id, partner_id),

    CONSTRAINT fk_bid_tender
        FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Logistics Partner bids on tenders. Sealed-envelope. v5.0 schema.';


-- ── 3. tp_assignments ────────────────────────────────────────────────────────
-- After the TP awards a bid, the winning Logistics Partner submits the actual
-- vehicle + driver they'll use. This row is the LP's "I commit to this trip"
-- handshake.
--
-- ⚠️ SCHEMA GAP: v5.0 schema doesn't include the obvious vehicle / driver columns
--   (vehicle_number, driver_name, etc.). Tracked in tender.md §10.2 as an open
--   question — for now, the LP's vehicle/driver detail goes into bids.notes as
--   a JSON string. Post-UAT we'll add proper columns.
CREATE TABLE tp_assignments (

    id          CHAR(36)        NOT NULL,
    tender_id   CHAR(36)        NOT NULL,

    -- The winning Logistics Partner. partner_tp_profiles.id; FK added later.
    partner_id  CHAR(36)        NOT NULL,

    -- How the assignment notification was delivered to the LP: WHATSAPP | SMS | IN_APP.
    -- Used by ops to trace which channel actually reached the LP.
    sent_via    VARCHAR(20)     NOT NULL,

    sent_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_ta_tender (tender_id),

    CONSTRAINT fk_ta_tender
        FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tender assignment broadcasts. SCHEMA GAP — vehicle/driver detail TBD post-UAT.';


-- ── 4. tender_order_refs ─────────────────────────────────────────────────────
-- Many-to-many link from tenders to PTL orders (BR-ORD-15). One PTL tender can
-- consolidate N orders that share a route; an order can be linked to at most
-- one tender at a time (enforced by application logic — no DB UNIQUE because
-- historical re-links should still be visible in the row history).
CREATE TABLE tender_order_refs (

    id         CHAR(36)        NOT NULL,
    tender_id  CHAR(36)        NOT NULL,
    order_id   CHAR(36)        NOT NULL
               COMMENT 'PTL orders only, status >= ACKNOWLEDGED (BR-ORD-15)',

    linked_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Same (tender, order) pair can't be linked twice — application unlinks the
    -- previous row before inserting if re-linking is desired.
    UNIQUE KEY uq_tor_tender_order (tender_id, order_id),

    INDEX idx_tor_tender (tender_id),
    INDEX idx_tor_order  (order_id),

    CONSTRAINT fk_tor_tender
        FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE,

    -- RESTRICT on order side — tender cancellation shouldn't cascade-delete
    -- order references. Application unlinks them explicitly.
    CONSTRAINT fk_tor_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='M:N link tenders ↔ PTL orders. BR-ORD-15. v5.0 schema.';
