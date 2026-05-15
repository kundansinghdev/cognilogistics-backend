-- Single-use setup-pin and reset-pin tokens (replaces in-memory store for multi-instance deploys).
CREATE TABLE auth_temp_tokens (
    token          CHAR(32)     NOT NULL,
    user_id        CHAR(36)     NULL,
    phone          VARCHAR(15)  NOT NULL,
    scope          VARCHAR(20)  NOT NULL,
    expires_at     DATETIME     NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token),
    INDEX idx_auth_temp_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Short-lived OTP-flow tokens; consumed on setup-pin / reset-pin/set.';
