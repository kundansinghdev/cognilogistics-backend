-- =============================================================================
-- Migration:  V3__refresh_tokens.sql   (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     auth
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Persists refresh tokens for JWT renewal. Refresh tokens are how this platform
--   achieves revocability on top of stateless JWT access tokens — DD-03.
--
-- LIFECYCLE (see auth.md §3.4):
--   1. On successful login (PIN or OTP): a 32-byte random raw token is generated,
--      its SHA-256 hex digest is stored in `token_hash`, and the raw token is sent
--      to the client. The raw token never enters the database.
--   2. On `POST /auth/refresh`: the client sends the raw token; server hashes and
--      looks up by `token_hash`. If found and active, the row is marked revoked
--      (rotation) and a NEW row is inserted with a fresh raw token. The old token
--      cannot be reused — single-use rotation.
--   3. On logout: the supplied token's row is marked revoked.
--   4. On PIN reset: ALL of the user's tokens are revoked (lost-device protection).
--
-- ACCESS-TOKEN TTL is driven by `device_type` (15 min web / 1 hr mobile, see
-- AuthProperties). Refresh-token TTL is 30 days regardless of device type — schema
-- v5.0 deliberately consolidates this rather than splitting per device type.
--
-- KEY CONSTRAINTS:
--   - UNIQUE (user_id, device_id): enforces "one active token per user per device"
--     at the DB level. New login from the same device implicitly revokes the
--     previous row before inserting the new one.
--   - INDEX (token_hash): the dominant lookup path during /auth/refresh.
--
-- WHY SHA-256 (NOT bcrypt) FOR TOKEN_HASH:
--   Refresh tokens are random 32-byte values from a CSPRNG — already high-entropy.
--   bcrypt's brute-force resistance is irrelevant; the cost would only slow refresh.
--   SHA-256 is the right hash for high-entropy secrets stored at rest.
-- =============================================================================

CREATE TABLE refresh_tokens (

    id           CHAR(36)        NOT NULL,

    -- Owning user. CASCADE so deleting a user invalidates all their sessions.
    user_id      CHAR(36)        NOT NULL,

    -- Client-supplied stable device identifier. Together with user_id forms the
    -- "session per device" granularity. VARCHAR(255) to accept varied formats
    -- (browser fingerprints, native app device IDs, etc.).
    device_id    VARCHAR(255)    NOT NULL,

    -- Drives access-token TTL: 15 min for WEB, 1 hr for MOBILE.
    -- Stored on the refresh row so /auth/refresh can re-issue the access token
    -- with the correct TTL without the client re-supplying device_type.
    device_type  VARCHAR(10)     NOT NULL,

    -- SHA-256 hex of the raw 32-byte token. CHAR(64) — exactly 64 hex chars.
    -- The raw token never enters the database.
    token_hash   CHAR(64)        NOT NULL,

    -- Computed at insert: now + 30 days. Active = revoked_at IS NULL AND expires_at > NOW().
    expires_at   DATETIME        NOT NULL,

    -- Set when the token is consumed (logout, refresh-rotation, or revoke-all).
    -- NULL = active. Once set, the token cannot be used.
    revoked_at   DATETIME        NULL,

    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- DB-level enforcement of "one active session per device" — the application
    -- layer revokes the old row before inserting the new one. The UNIQUE here is
    -- the safety net.
    -- Names match schema.sql v5.0 lines 100-103 (uq_rt_*, idx_rt_*, fk_rt_*).
    UNIQUE KEY uq_rt_user_device (user_id, device_id),

    -- Lookup by hash during /auth/refresh — the dominant query.
    INDEX idx_rt_token_hash (token_hash),

    -- "Show me all of this user's sessions" — used by revoke-all-on-PIN-reset.
    INDEX idx_rt_user (user_id),

    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DB-backed refresh tokens — JWT revocability layer. DD-03. v5.0.';
