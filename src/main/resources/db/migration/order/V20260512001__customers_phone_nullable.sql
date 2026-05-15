-- =============================================================================
-- Migration:  V20260512001__customers_phone_nullable.sql
-- Module:     order
-- Date:       2026-05-12
-- PR:         R2 (create-order UX: phone truly optional)
--
-- WHAT THIS MIGRATION DOES:
--   Relaxes the NOT NULL constraint on customers.phone so TP staff can create
--   an order for a brand-new customer (not yet in Company Master) without
--   being forced to enter a WhatsApp number up front.
--
-- WHY:
--   Product decision (UAT feedback): "I told you to make this optional. Why
--   it is asking?" The TP can add the phone later via customer edit; for
--   now an order must be creatable with name + GSTIN + locations only.
--
-- SAFETY:
--   - MySQL allows multiple NULL values inside a UNIQUE index, so the
--     existing uq_customer_phone unique key keeps its meaning (no two
--     customers can share the same non-null phone) while permitting any
--     number of phone-less shadow customers.
--   - Existing rows are unaffected — all current customers already have a
--     phone, so the NOT NULL → NULL transition can never fail validation.
-- =============================================================================

ALTER TABLE customers
    MODIFY COLUMN phone VARCHAR(15) NULL
        COMMENT 'Customer phone. Globally unique when set; NULL allowed for shadow customers created before phone is known.';
