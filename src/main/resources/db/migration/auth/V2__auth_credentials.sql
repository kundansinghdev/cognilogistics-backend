-- =============================================================================
-- Migration:  V2__auth_credentials.sql   (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     auth
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Stores 4-digit PIN credentials and brute-force lockout state for TP-modality
--   users (TP_ADMIN / TP_TRANSPORT_MANAGER / PARTNER_TP).
--
-- ⚠️ IMPORTANT — ROLES THAT DO NOT HAVE A ROW HERE:
--   CUSTOMER and COGNILOGISTIC_ADMIN authenticate via OTP-only (no PIN). No
--   `auth_credentials` row is ever created for those roles. Code that joins
--   `users` to `auth_credentials` MUST use LEFT JOIN, not INNER JOIN, and
--   handle the absent-row case for OTP-only roles.
--   See auth.md §3.4 (CUSTOMER) and §3.5 (COGNILOGISTIC_ADMIN).
--
-- KEY DESIGN DECISIONS:
--   - PK is `user_id` directly. NO surrogate `id` column. One row per user, period.
--   - `pin_hash` is bcrypt cost-10. Schema column VARCHAR(60) accommodates the
--     standard bcrypt format ($2a$10$...).
--   - `failed_attempts` resets to 0 on every successful login. Brute-force lockout
--     trips at 5 (BR-AUTH-06).
--   - `locked_until` is populated when failed_attempts hits 5; lockout duration is
--     30 minutes (BR-AUTH-07). After that timestamp passes, login is allowed again.
--     The forgot-PIN flow (auth.md §3.3) clears the lockout immediately.
--
-- TRANSACTION NOTE for service code:
--   AuthService.login uses @Transactional(noRollbackFor = ApiException.class) so
--   that the failed_attempts increment is persisted even when the login throws
--   401 INVALID_PIN. Without that, the brute-force counter would roll back and
--   the lockout mechanism would be defeated.
-- =============================================================================

CREATE TABLE auth_credentials (

    -- PK and FK to users.id. ON DELETE CASCADE so deleting a user removes credentials.
    user_id          CHAR(36)        NOT NULL,

    -- bcrypt(PIN, cost=10). Never store the raw PIN. Never log it.
    pin_hash         VARCHAR(60)     NOT NULL,

    -- Counter incremented on every wrong-PIN login attempt. Resets to 0 on success
    -- and on PIN reset. BR-AUTH-06.
    failed_attempts  INT             NOT NULL DEFAULT 0,

    -- When non-null and in the future, the account is locked. Login returns
    -- 423 ACCOUNT_LOCKED with this timestamp in the error details. BR-AUTH-07.
    locked_until     DATETIME        NULL,

    -- Audit timestamps.
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),

    -- Cascade so user deletion (e.g. ops cleanup) drops credentials in the same op.
    -- Constraint name `fk_auth_cred_user` matches schema.sql v5.0 line 87.
    CONSTRAINT fk_auth_cred_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PIN credentials for TP-modality users. v5.0 schema.';
