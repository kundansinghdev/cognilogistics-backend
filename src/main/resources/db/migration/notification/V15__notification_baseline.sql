-- =============================================================================
-- Migration:  V15__notification_baseline.sql   (NEW BASELINE — schema.sql v5.0)
-- Module:     notification
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the three notification-module tables in one go. All three are tightly
--   coupled (preferences gate notification_log writes; device_registrations is
--   the push routing list).
--
--     1. notification_preferences  — per-user channel opt-in (SMS / WhatsApp / push / email)
--     2. notification_log          — append-only record of every notification dispatched
--     3. device_registrations      — Azure Notification Hubs handles for push (post-UAT)
--
-- HOW IT FITS:
--   Other modules publish ApplicationEvents (OrderDelivered, BidSubmitted, etc.).
--   The notification module's @EventListener consumes them after the originating
--   transaction commits, looks up the recipient(s), filters by their preferences,
--   renders templates, dispatches through channel adapters, and writes a row here.
--
-- ⚠️ SCHEMA GAP — IDEMPOTENCY:
--   Older drafts had {event_id, channel, user_id} UNIQUE on notification_log so
--   the same event couldn't deliver twice on the same channel. v5.0 dropped the
--   event_id column. For UAT we'll dedupe in application memory (Caffeine cache
--   of recent event ids); horizontal scaling will need either the column back or
--   Redis-backed dedup. Tracked in notification.md §10.1.
-- =============================================================================


-- ── 1. notification_preferences ──────────────────────────────────────────────
-- One row per user. Created automatically on first login by the auth flow's
-- post-commit hook (so a user always has prefs to consult). Default channel
-- enables: SMS=true, WhatsApp=true, Push=false (post-UAT infra).
CREATE TABLE notification_preferences (

    -- The user this prefs row belongs to. PK directly — one row per user.
    user_id           CHAR(36)  NOT NULL,

    -- Default true: SMS is the most reliable channel for Indian B2B logistics.
    sms_enabled       BOOLEAN   NOT NULL DEFAULT TRUE,

    -- Default true: WhatsApp template generation is the pilot's pseudo-channel
    -- (TP forwards manually; no BSP integration yet).
    whatsapp_enabled  BOOLEAN   NOT NULL DEFAULT TRUE,

    -- Default false: Push infra (Azure Notification Hubs) is post-UAT. Users can
    -- opt in once it's live.
    push_enabled      BOOLEAN   NOT NULL DEFAULT FALSE,

    PRIMARY KEY (user_id),

    -- Cascade so deleting a user wipes their prefs.
    CONSTRAINT fk_np_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-user channel opt-in. v5.0 schema.';


-- ── 2. notification_log ──────────────────────────────────────────────────────
-- Append-only. Every notification dispatch — successful, failed, or skipped by
-- preference — produces a row here. Used by support to answer "did Mrs Patel
-- receive the OrderDelivered notification?"
CREATE TABLE notification_log (

    id            CHAR(36)        NOT NULL,
    user_id       CHAR(36)        NOT NULL,

    -- SMS / WHATSAPP / PUSH / EMAIL / IN_APP. The schema column is VARCHAR(20)
    -- so adding a new channel doesn't need a migration.
    channel       VARCHAR(20)     NOT NULL,

    -- Template name used (e.g. "ORDER_DELIVERED_HI"). NULL for ad-hoc messages.
    template      VARCHAR(100)    NULL,

    -- SENT / FAILED / PENDING / SKIPPED_PREFERENCE / GENERATED. Free-text — the
    -- enum string set is the application's convention; v5.0 schema doesn't
    -- enforce CHECK on the value set.
    --
    -- GENERATED is specific to WHATSAPP template channel: we generated a wa.me
    -- link + template text for the TP user to forward manually; the actual
    -- delivery is out of our control (no BSP in pilot).
    --
    -- SKIPPED_PREFERENCE is a "we tried but the user opted out" record — we
    -- log it for compliance ("we tried to deliver this notification" auditing).
    status        VARCHAR(20)     NOT NULL,

    sent_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Free-text error message if status=FAILED. Helps support diagnose Twilio
    -- errors without consulting external logs.
    error_message TEXT            NULL,

    PRIMARY KEY (id),

    -- Hot path: in-app feed reads "every IN_APP entry for this user, newest first."
    INDEX idx_nl_user (user_id),

    CONSTRAINT fk_nl_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Notification audit. Append-only. v5.0 schema.';


-- ── 3. device_registrations ──────────────────────────────────────────────────
-- Push notification routing — registers a device's Azure Notification Hubs handle
-- for a user. Post-UAT feature; the table exists in v5.0 baseline so the column
-- shape is fixed once the push channel goes live.
CREATE TABLE device_registrations (

    id                       CHAR(36)        NOT NULL,
    user_id                  CHAR(36)        NOT NULL,

    -- Stable client-generated device identifier. Same shape as
    -- refresh_tokens.device_id (auth module). The UNIQUE (user_id, device_id)
    -- below means re-registration of an existing device updates the row's
    -- notification_hub_handle in place rather than creating a duplicate.
    device_id                VARCHAR(255)    NOT NULL,

    -- IOS / ANDROID. Drives the Notification Hubs platform-specific payload format.
    platform                 VARCHAR(10)     NOT NULL,

    -- Opaque handle returned by Azure Notification Hubs after registration.
    -- Up to 500 chars — NH handles can be long base64 strings.
    notification_hub_handle  VARCHAR(500)    NOT NULL,

    registered_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- One row per (user, device). Re-registration UPDATES this row.
    UNIQUE KEY uq_device_user (user_id, device_id),
    INDEX idx_device_user (user_id),

    CONSTRAINT fk_device_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Mobile device tokens for push notifications. Post-UAT. v5.0 schema.';
