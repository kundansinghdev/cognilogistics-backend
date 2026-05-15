-- =============================================================================
-- Migration:  V20260507101__device_trust.sql   (NEW — schema.sql v5.0 introduces this table)
-- Module:     auth
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `device_trust` table — a marker table recording which (user, device)
--   pairs have been "trusted" by a successful PIN entry.
--
-- INTENDED USE (post-UAT — not implemented yet):
--   The 15-day "trust this device" UX where a returning TP user on a known device
--   skips PIN re-entry inside the trust window. Service-side logic: on successful
--   PIN entry, INSERT or UPDATE this row with `trusted_at = NOW()`. On subsequent
--   logins, if the device row exists and `trusted_at + 15 days > NOW()`, allow
--   silent re-issue of tokens without prompting for the PIN.
--
-- ⚠️ SCHEMA SIMPLIFIED IN v5.0:
--   Earlier drafts had `trusted_until` and `last_pin_at` columns to encode the
--   15-day window explicitly. v5.0 dropped them — the trust window is implemented
--   at the application layer (compute `trusted_at + 15 days` on read) rather than
--   in the schema. This keeps the table tiny and the rule easy to change.
--
-- ROLES THAT USE THIS TABLE:
--   Only PIN-modality roles: TP_ADMIN, TP_TRANSPORT_MANAGER, PARTNER_TP. The
--   OTP-only roles (CUSTOMER, COGNILOGISTIC_ADMIN) re-OTP every session anyway —
--   no PIN, no trust window.
-- =============================================================================

CREATE TABLE device_trust (

    id           CHAR(36)        NOT NULL,

    -- The user who trusts the device. CASCADE so user-deletion drops trust rows.
    user_id      CHAR(36)        NOT NULL,

    -- The device identifier — same shape as `refresh_tokens.device_id`. The pair
    -- (user_id, device_id) is the trust granularity.
    device_id    VARCHAR(255)    NOT NULL,

    -- When the device was last trusted by a fresh PIN entry. Application layer
    -- computes the window boundary: trusted_at + 15 days.
    trusted_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- "One trust row per (user, device)" — enforced at DB level. Service code
    -- upserts on successful PIN entry.
    -- Names match schema.sql v5.0 lines 124-125 (uq_dt_*, fk_dt_*).
    UNIQUE KEY uq_dt_user_device (user_id, device_id),

    CONSTRAINT fk_dt_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Trusted-device markers for the 15-day skip-PIN UX. v5.0 schema.';
