-- =============================================================================
-- Migration:  V13__integration_baseline.sql   (BASELINE — schema.sql v5.0)
-- Module:     integration
-- Date:       2026-05-07 (revised 2026-05-08)
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the four integration audit tables. Aligned with the canonical
--   schema.sql v5.0 column shapes (revised 2026-05-08 to fix a drift between
--   V13's earlier draft and the canonical spec — column names like consent_at /
--   vehicle_reg / dl_number lengths / external_api_audit_log fields had drifted
--   and would have failed Hibernate's ddl-auto: validate at boot).
--
--     1. vahan_consent_log    — consent before each Vahan vehicle-registry lookup
--     2. sarathi_consent_log  — consent before each Sarathi DL-registry lookup
--     3. gst_consent_log      — consent before each GST GSTIN lookup
--     4. external_api_audit_log — call-level audit (mock or real, latency, status)
--
-- BR-ORD-10 — VAHAN CONSENT IS MANDATORY:
--   Before calling the Vahan API for an order's vehicle, a row MUST exist in
--   `vahan_consent_log` for that (order_id, reg_number) pair. The check fires
--   even in mock mode (`VAHAN_MOCK=true`) — the consent log is what we'll show
--   to a DPDP auditor, regardless of whether the call was real or mocked.
--
-- ⚠️ Replaces legacy V11__vahan_consent_log.sql (BIGINT, with extra fields like
--    consent_text/consent_given/mock_mode that v5.0 dropped). Fresh DB required.
-- =============================================================================


-- ── 1. vahan_consent_log ─────────────────────────────────────────────────────
-- Pre-call consent for vehicle-registry lookups.
-- DPDP rule: never call Vahan without a recorded consent row first.
--
-- Schema reference: schema.sql v5.0 lines 676–686. FK names match those exactly
-- (fk_vcl_*) so the schema.sql full-load and Flyway-versioned paths produce
-- identical DBs.
CREATE TABLE vahan_consent_log (

    id           CHAR(36)        NOT NULL,
    order_id     CHAR(36)        NOT NULL,
    user_id      CHAR(36)        NOT NULL COMMENT 'User who gave consent',
    reg_number   VARCHAR(20)     NOT NULL COMMENT 'Vehicle registration number, UPPERCASE',
    consented_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_vcl_order (order_id),

    CONSTRAINT fk_vcl_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vcl_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pre-call consent record for Vahan (DPDP). Required before any Vahan API call.';


-- ── 2. sarathi_consent_log ───────────────────────────────────────────────────
-- Pre-call consent for driving-licence-registry lookups. Same pattern as Vahan,
-- keyed on the DL number rather than vehicle registration. dl_number is
-- VARCHAR(30) per schema (Indian DLs are 16 chars; the extra room covers
-- formatting variations like dashes).
--
-- Schema reference: schema.sql v5.0 lines 688–697.
CREATE TABLE sarathi_consent_log (

    id           CHAR(36)        NOT NULL,
    order_id     CHAR(36)        NOT NULL,
    user_id      CHAR(36)        NOT NULL,
    dl_number    VARCHAR(30)     NOT NULL,
    consented_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_scl_order (order_id),

    CONSTRAINT fk_scl_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pre-call consent record for Sarathi DL lookup.';


-- ── 3. gst_consent_log ───────────────────────────────────────────────────────
-- Pre-call consent for GST GSTIN lookups. Tenant-scoped via tp_account_id —
-- GSTIN lookups happen at customer-creation / Company-Master time, where the
-- TP context is known but no order_id exists yet.
--
-- Schema reference: schema.sql v5.0 lines 699–707.
CREATE TABLE gst_consent_log (

    id            CHAR(36)        NOT NULL,
    tp_account_id CHAR(36)        NOT NULL,
    user_id       CHAR(36)        NOT NULL,
    gstin         VARCHAR(15)     NOT NULL,
    consented_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_gcl_tp (tp_account_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pre-call consent record for GST GSTIN lookup.';


-- ── 4. external_api_audit_log ────────────────────────────────────────────────
-- Generic "we called an external API" audit. Mock and real calls both write here
-- so ops can grep for "what calls did we attempt today, real or mocked?"
-- Body capture (request/response JSON) is intentionally NOT in v5.0 schema —
-- adds storage cost and PII redaction complexity. The audit is metadata-only.
--
-- Schema reference: schema.sql v5.0 lines 709–719. Earlier V13 draft had a
-- richer column set (endpoint / latency_ms / related_entity_*) that diverged
-- from canonical schema; revised here to match v5.0 exactly.
CREATE TABLE external_api_audit_log (

    id           CHAR(36)        NOT NULL,
    service      VARCHAR(20)     NOT NULL COMMENT 'VAHAN|SARATHI|GST',
    request_ref  CHAR(36)        NULL     COMMENT 'order_id or tp_account_id, depending on service',
    status_code  INT             NULL,
    response_ms  INT             NULL,
    mock_used    BOOLEAN         NOT NULL DEFAULT FALSE
                 COMMENT 'TRUE when VAHAN_MOCK / SARATHI_MOCK / GST_MOCK = true',
    called_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_eaal_service (service)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='External API call audit (mock or real). DPDP retention.';
