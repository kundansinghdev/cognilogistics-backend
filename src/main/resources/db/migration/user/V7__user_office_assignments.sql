-- =============================================================================
-- Migration:  V7__user_office_assignments.sql   (REWRITTEN — schema.sql v5.0 alignment)
-- Module:     user
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `user_office_assignments` join table — maps TP_TRANSPORT_MANAGER
--   users to the branch offices they're authorised to operate. Consulted during
--   every order action to enforce office-membership scoping (BR-OFF-05 et al.).
--
-- KEY DESIGN DECISIONS (v5.0):
--   - PRIMARY KEY is (user_id, office_id) — composite, no surrogate id column.
--     The pair IS the natural identity of the row; a surrogate would just consume
--     an index slot.
--   - REMOVED `assigned_at` column from legacy V7. v5.0 dropped this audit timestamp
--     since the audit trail (who assigned whom, when) lives in `audit_logs` rather
--     than on the row itself. The membership is a binary fact: either the row exists
--     (member) or it doesn't (not member).
--   - Both FKs use ON DELETE CASCADE — deleting a user OR an office removes any
--     dangling membership rows automatically. This matches the legacy behaviour
--     ("rows are not soft-deleted; physically deleted to remove access") and also
--     keeps Spring Data's `deleteByUserId` / `deleteByOfficeId` operations clean.
--
-- USAGE (BR-OFF / BR-ORD):
--   - TP_ADMIN bypasses this table entirely (admin sees all offices).
--   - TP_TRANSPORT_MANAGER must have a row here matching the order's
--     `office_id` to perform any state-changing action on that order.
--   - The order module calls `UserOfficeAssignmentRepository.existsByUserIdAndOfficeId`
--     on every confirm-fleet / start-transit / deliver / cancel.
-- =============================================================================

CREATE TABLE user_office_assignments (

    -- The user being granted access. CHAR(36) UUID. CASCADE so user deletion
    -- (e.g. ops cleanup) wipes their memberships.
    user_id     CHAR(36)        NOT NULL,

    -- The office the user is assigned to. CHAR(36) UUID. CASCADE so office hard-deletion
    -- wipes memberships (in practice offices are soft-deleted via is_active=false, so
    -- this CASCADE rarely fires — but it's the right semantics if it does).
    office_id   CHAR(36)        NOT NULL,

    -- Composite PK — the pair IS the row's identity.
    PRIMARY KEY (user_id, office_id),

    -- Required by Hibernate to back the office-scoped lookup
    -- `UserOfficeAssignmentRepository.findByOfficeId(officeId)` efficiently
    -- (the PK index already covers `findByUserId`).
    INDEX idx_uoa_office (office_id),

    CONSTRAINT fk_uoa_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_uoa_office
        FOREIGN KEY (office_id) REFERENCES offices(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Many-to-many: TP_TRANSPORT_MANAGER → offices. Composite PK. v5.0 schema.';
