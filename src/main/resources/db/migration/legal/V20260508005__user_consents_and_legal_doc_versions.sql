-- =============================================================================
-- Migration:  V20260508005__user_consents_and_legal_doc_versions.sql
-- Module:     legal
-- Date:       2026-05-08
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Adds the schema for IT Act 2000 / DPDPA 2023 compliant T&C / Privacy
--   consent capture at signup time. See BACKEND_SPEC_TC_CONSENT.md (front-end
--   contract) for the full design rationale.
--
--     1. legal_doc_versions  — currently published version per doc type
--                              (small, mostly-static, FE reads on signup mount)
--     2. user_consents       — append-only audit log of every consent event
--                              (per (user, docType, version) — UNIQUE keyed)
--
-- WHY APPEND-ONLY:
--   We need full history including future re-acceptance events. Boolean flags
--   on the users table would lose that. The DPDPA / IT Act audit story is
--   "prove which user agreed to which version at what time from where" —
--   that's exactly what this table answers.
--
-- IDs:
--   user_consents.id is CHAR(36) UUID like every other id in this codebase
--   (project convention since PR A2). Spec's `BIGSERIAL` is PostgreSQL-only;
--   we use UUID strings for portability and to avoid leaking row counts.
--
-- ON DELETE CASCADE on user_id:
--   Default per spec §3.1. If legal subsequently rules consent rows must
--   outlive user erasure as evidence, ALTER to ON DELETE SET NULL and make
--   user_id nullable in a follow-up migration. Track in the consent ticket.
-- =============================================================================


-- ── 1. legal_doc_versions ────────────────────────────────────────────────────
-- Tiny lookup table. PK is the doc_type itself — there's only ever one current
-- version per doc type. New versions UPDATE this row in place; the historical
-- rows in user_consents preserve which version each user signed.
CREATE TABLE legal_doc_versions (

    -- TERMS | PRIVACY. Free-text VARCHAR rather than ENUM — adding new doc
    -- types (e.g. MARKETING_OPT_IN, COOKIE_POLICY) doesn't need a migration.
    doc_type      VARCHAR(16)     NOT NULL,

    -- ISO date string per spec §2 ("2026-05-08"). VARCHAR(32) leaves room for
    -- future schemes (semver, release codenames) without a migration.
    version       VARCHAR(32)     NOT NULL,

    -- When this version became current. Timezone is UTC at the JDBC layer.
    published_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (doc_type)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Currently published version of each legal doc. FE reads on signup mount.';


-- Seed the two doc types FE expects today. Idempotent on re-run via the
-- INSERT ... ON DUPLICATE KEY clause: if the row exists we update its
-- version + published_at to the seed values, which is what we want for a
-- fresh schema setup. Once a real legal-update workflow is in place, ops
-- runs UPDATE statements instead of re-applying this migration.
INSERT INTO legal_doc_versions (doc_type, version, published_at)
VALUES
  ('TERMS',   '2026-05-08', CURRENT_TIMESTAMP),
  ('PRIVACY', '2026-05-08', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
  version      = VALUES(version),
  published_at = VALUES(published_at);


-- ── 2. user_consents ─────────────────────────────────────────────────────────
-- Append-only audit log. Every accepted-consent click writes one row per doc
-- type. The UNIQUE (user_id, doc_type, doc_version) constraint blocks replays
-- at the DB level — duplicate inserts of the same triple raise a constraint
-- error (caught by the service and translated to an idempotent success).
CREATE TABLE user_consents (

    id           CHAR(36)        NOT NULL,
    user_id      CHAR(36)        NOT NULL,

    -- TERMS | PRIVACY (free-text per the doc_type design above).
    doc_type     VARCHAR(16)     NOT NULL,

    -- Version that was accepted. Must equal a row in legal_doc_versions at
    -- write time (validated in service code, not at the DB level — keeping
    -- the FK absent here lets future doc-version rotations not break old
    -- consent rows).
    doc_version  VARCHAR(32)     NOT NULL,

    -- Server timestamp at consent. NEVER trust client-supplied timestamps.
    accepted_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- IPv6-compatible (45 chars covers ::ffff:255.255.255.255 worst case).
    -- Server extracts from request after stripping trusted proxy hops —
    -- never read X-Forwarded-For blindly. See spec §5.3.
    ip_address   VARCHAR(45)     NULL,

    -- TEXT (vs VARCHAR) because Chrome / Edge / mobile UAs are routinely
    -- 200+ chars and growing; capping would silently truncate evidence.
    user_agent   TEXT            NULL,

    PRIMARY KEY (id),

    -- A user can only "accept v2026-05-08 of TERMS" once. Replays / duplicate
    -- inserts of the same triple fail at the DB level (caught and translated
    -- to an idempotent success in service code).
    UNIQUE KEY uq_user_consent (user_id, doc_type, doc_version),

    -- Hot reads: "all of this user's consents" (ordered by accepted_at DESC
    -- in the admin endpoint).
    INDEX idx_uc_user (user_id),
    -- Audit aggregation: "how many users accepted v2026-05-08 of TERMS?"
    INDEX idx_uc_doc  (doc_type, doc_version),

    CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only audit log of T&C / Privacy consent events. DPDPA evidence.';
