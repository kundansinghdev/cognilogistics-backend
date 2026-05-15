-- =============================================================================
-- Migration:  V20260508004__impersonation_audit_log.sql
-- Module:     platform
-- Date:       2026-05-08
-- PR:         R6 (admin-portal endpoint surface)
--
-- WHAT THIS MIGRATION DOES:
--   Adds the impersonation_audit_log table — every COGNILOGISTIC_ADMIN
--   "Log in as" session writes a row here on session start and updates it on
--   session end. The actions_performed counter is incremented by audit hooks
--   on every mutation made during the session.
--
-- WHY:
--   Compliance + customer-trust requirement. The platform supports ops
--   impersonating tenants for support, but every such session must be
--   traceable. v5.0 schema declares this table at lines 277–297; this
--   migration is the project's first Flyway entry that creates it.
--
-- DEPENDENCY ORDER:
--   References users(id) and tp_accounts(id). Both already exist (V1, V5).
-- =============================================================================

CREATE TABLE impersonation_audit_log (

    id                    CHAR(36)        NOT NULL COMMENT 'UUID server-side.',
    admin_user_id         CHAR(36)        NOT NULL COMMENT 'FK users.id of COGNILOGISTIC_ADMIN who initiated impersonation.',
    admin_name            VARCHAR(255)    NULL     COMMENT 'Denormalised admin name for fast audit reads.',

    target_type           VARCHAR(20)     NOT NULL COMMENT 'TP|PARTNER|CUSTOMER — which account type was entered.',
    target_tp_account_id  CHAR(36)        NULL     COMMENT 'FK tp_accounts.id. Set for TP impersonation.',
    target_user_id        CHAR(36)        NULL     COMMENT 'FK users.id. Set for PARTNER / CUSTOMER impersonation.',
    target_name           VARCHAR(255)    NULL     COMMENT 'Denormalised name of the org or user entered.',

    session_started_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_ended_at      DATETIME        NULL     COMMENT 'NULL = session still active.',
    actions_performed     INT             NOT NULL DEFAULT 0
                          COMMENT 'Counter incremented on every write during impersonation.',
    notes                 VARCHAR(500)    NULL     COMMENT 'Optional reason entered by admin before starting.',

    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_ial_admin   (admin_user_id),
    INDEX idx_ial_tp      (target_tp_account_id),
    INDEX idx_ial_started (session_started_at),

    CONSTRAINT fk_ial_admin FOREIGN KEY (admin_user_id)        REFERENCES users(id)       ON DELETE RESTRICT,
    CONSTRAINT fk_ial_tp    FOREIGN KEY (target_tp_account_id) REFERENCES tp_accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_ial_user  FOREIGN KEY (target_user_id)       REFERENCES users(id)       ON DELETE SET NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit log for every CogniLogistic admin impersonation session. Append-only.';
