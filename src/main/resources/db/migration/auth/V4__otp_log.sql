-- =============================================================================
-- Migration:  V4__otp_log.sql        (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     auth
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Records every OTP issuance for audit + idempotency. Used by:
--     - TP first-login signup flow (purpose=FIRST_LOGIN)
--     - PIN-reset flow            (purpose=PIN_RESET)
--     - Customer Portal sign-in   (purpose=FIRST_LOGIN — same enum, different downstream)
--     - COGNILOGISTIC_ADMIN login (purpose=FIRST_LOGIN)
--
-- ⚠️ ONLY TWO PURPOSE VALUES IN v5.0 SCHEMA: FIRST_LOGIN | PIN_RESET
--   Older drafts had UNLOCK and CUSTOMER_ACTIVATION; v5.0 dropped them. The
--   lockout-clear path is "use the PIN-reset flow," not a separate UNLOCK OTP.
--   Customer-portal each-session login uses FIRST_LOGIN — the table is just an
--   audit log, the purpose field is the enum constraint.
--
-- KEY DESIGN DECISIONS:
--   - `otp_hash` is bcrypt (VARCHAR(60)). v5.0 deliberately uses bcrypt for OTP
--     storage even though OTPs are short-lived — defense in depth in case the DB
--     leaks. Cost factor 10 matches PIN hashing.
--   - `verified BOOLEAN` (NOT `used_at` / `consumed_at`). The flag flips to TRUE
--     on successful verification — single-use enforcement. Older drafts used a
--     timestamp column; v5.0 simplified.
--   - 10-minute TTL: `expires_at = sent_at + 10 minutes`. Set at insert time.
--     CLAUDE.md §4 confirms 10-minute window.
--   - NO foreign key to `users`. OTPs are issued by phone, BEFORE a user row may
--     exist (TP first-login signup creates the user only AFTER OTP verification).
--     The phone column is the join key.
--
-- INDEX:
--   `idx_otp_phone` — the dominant query: "fetch the most-recent unverified OTP
--   for this phone+purpose." Service code does:
--       SELECT * FROM otp_log
--        WHERE phone = ? AND purpose = ? AND verified = FALSE
--        ORDER BY sent_at DESC LIMIT 1
--   covering this with a single index column on phone is sufficient at UAT volumes.
-- =============================================================================

CREATE TABLE otp_log (

    id           CHAR(36)        NOT NULL,

    -- Phone the OTP was sent to. E.164. No FK to users — OTP can be issued before
    -- a users row exists (signup case).
    phone        VARCHAR(15)     NOT NULL,

    -- bcrypt(otp, cost=10). VARCHAR(60) fits the standard $2a$10$... format.
    -- The raw OTP NEVER enters the database. Verification: bcrypt-compare submitted
    -- against this hash.
    otp_hash     VARCHAR(60)     NOT NULL,

    -- Why the OTP was issued. Schema constraint: FIRST_LOGIN | PIN_RESET (only).
    -- See header for what each purpose covers downstream.
    purpose      VARCHAR(20)     NOT NULL,

    -- When the OTP was generated and (presumably) delivered to the user.
    sent_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Single-use flag. Flipped to TRUE on successful verification. Subsequent
    -- verify attempts on the same row return OTP_USED.
    verified     BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Validity cut-off. Always sent_at + 10 minutes. Past this point, verification
    -- returns OTP_EXPIRED regardless of verified-flag state.
    expires_at   DATETIME        NOT NULL,

    PRIMARY KEY (id),

    -- Phone is the dominant lookup key. Service code adds purpose + verified=FALSE
    -- as filter conditions in the WHERE clause; index on phone alone is fine at
    -- UAT cardinality (most phones have <5 OTPs in flight).
    INDEX idx_otp_phone (phone)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='OTP audit log. bcrypt-stored. 10-min TTL. v5.0 schema.';
