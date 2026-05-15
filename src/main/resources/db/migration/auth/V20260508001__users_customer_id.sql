-- =============================================================================
-- Migration:  V20260508001__users_customer_id.sql
-- Module:     auth
-- Date:       2026-05-08
-- PR:         R1 (front-end-driven gap closure)
--
-- WHAT THIS MIGRATION DOES:
--   Adds an explicit users.customer_id FK column so a CUSTOMER-role user is
--   linked to their customers row by id, not just by phone uniqueness on both
--   tables. Mirrors the existing tp_account_id and partner_tp_profile_id
--   pattern.
--
-- WHY:
--   The customer portal scopes "my orders" by customer_id. Today we discover
--   the customer by JOINing on phone, which works but is fragile (phone
--   updates, multi-customer-per-phone edge cases). Direct FK is enforceable,
--   indexable, and cheap to JOIN.
--
-- SOURCE:
--   Section 7 of FinalVersion/database/V20260507006__partner_groups_and_tender_broadcast.sql.
--   That single file is split per-module here to match this project's
--   migration layout (HANDOFF.md §7.2).
-- =============================================================================

-- 1. Add the column.
ALTER TABLE users
    ADD COLUMN customer_id CHAR(36) NULL DEFAULT NULL
        COMMENT 'FK customers.id. Set for role=CUSTOMER once OTP onboarding completes. NULL otherwise.'
        AFTER partner_tp_profile_id;

-- 2. Index it — every "find user by customer_id" lookup uses this.
ALTER TABLE users
    ADD INDEX idx_users_customer (customer_id);

-- 3. FK to customers. customers already exists (V8), so no circular ref to defer.
ALTER TABLE users
    ADD CONSTRAINT fk_users_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;
