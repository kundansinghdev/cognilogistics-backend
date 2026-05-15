-- =============================================================================
-- Migration:  V11__order_status_log.sql   (NEW BASELINE — schema.sql v5.0 alignment)
-- Module:     order
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `order_status_log` table — append-only record of every status
--   transition an order goes through.
--
-- BR-ORD-06 — STATUS LOG IS MANDATORY:
--   Every status transition MUST insert a row here, including the initial
--   CREATED transition (where from_status is NULL). The order module's
--   OrderStateMachine writes this row inside the same transaction as the
--   `orders.status` update so the log is always in sync with reality.
--
-- COMPLEMENTS audit_logs:
--   This is a domain-specific log focused on order lifecycle. The cross-cutting
--   `audit_logs` table (platform module) records the SAME transitions with the
--   richer schema (impersonation context, IP, JSON deltas) but is more verbose
--   to query. Order-detail UI uses order_status_log because it's narrow and
--   ordered by triggered_at; ops debugging uses audit_logs.
-- =============================================================================

CREATE TABLE order_status_log (

    id                  CHAR(36)        NOT NULL,
    order_id            CHAR(36)        NOT NULL,

    -- The status the order was in before the transition. NULL only for the
    -- initial CREATED row (an order has no "before" state).
    from_status         VARCHAR(30)     NULL,

    -- The status the order moved into. Always set.
    to_status           VARCHAR(30)     NOT NULL,

    -- The user who triggered the transition. NULL for system-driven transitions
    -- (e.g. scheduled-job auto-cancellation post-UAT — not currently in scope,
    -- but the column allows it without a future migration).
    triggered_by        CHAR(36)        NULL,

    -- Server timestamp of the transition. Set in service code (not via DB
    -- default) so test fixtures with a fixed Clock produce deterministic logs.
    triggered_at        DATETIME        NOT NULL,

    -- Free-text notes — used for cancel reasons especially (BR-ORD-02), but
    -- also fleet-confirm notes, etc.
    notes               VARCHAR(500)    NULL,

    PRIMARY KEY (id),

    -- Hot path: "show me this order's history" — order detail timeline.
    INDEX idx_osl_order (order_id),

    -- Cascade delete with the parent order. Status log is meaningless without
    -- the order itself.
    CONSTRAINT fk_osl_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Order lifecycle log — append-only. BR-ORD-06. v5.0 schema.';
