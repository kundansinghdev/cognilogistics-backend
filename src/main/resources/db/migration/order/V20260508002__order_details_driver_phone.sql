-- =============================================================================
-- Migration:  V20260508002__order_details_driver_phone.sql
-- Module:     order
-- Date:       2026-05-08
-- PR:         R1 (front-end-driven gap closure)
--
-- WHAT THIS MIGRATION DOES:
--   Adds order_details.driver_phone — the front-end's FleetConfirmModal
--   collects driverPhone alongside driverName and driverDl. Today the column
--   doesn't exist; the value is silently dropped server-side.
--
-- WHY:
--   The TP needs to call/WhatsApp the driver during transit. Driver phone is
--   already first-class data on the front-end — backend persists it so the
--   notification module's WhatsApp dispatch can reach the driver directly.
--
-- SOURCE:
--   Section 1 of FinalVersion/database/V20260507006__partner_groups_and_tender_broadcast.sql.
-- =============================================================================

ALTER TABLE order_details
    ADD COLUMN driver_phone VARCHAR(15) NULL DEFAULT NULL
        COMMENT 'Driver contact phone — captured at FLEET_CONFIRMED. Optional. Used for WhatsApp / SMS dispatch comms.'
        AFTER driver_name;
