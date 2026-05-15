-- =============================================================================
-- Migration:  V20260508006__seed_uat_data.sql
-- Module:     seed (UAT-only deterministic test fixtures)
-- Date:       2026-05-08
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Seeds the canonical UAT test fixtures so a fresh DB has working
--   admin / TP / customer rows immediately after Flyway runs. Mirrors the
--   seed block at the bottom of `database/schema.sql` but lives in a Flyway
--   migration so dropping + recreating the DB doesn't lose the test data.
--
--   Without this, every fresh DB requires the QA / dev person to manually
--   paste the seed block into MySQL Workbench — which has bitten the
--   front-end team multiple times during integration testing.
--
-- IDEMPOTENCY:
--   All inserts use INSERT IGNORE so the migration is safe to re-run against
--   a partially-seeded DB. Flyway itself only runs each Vn migration once,
--   but the IGNORE pattern protects against the case where someone already
--   ran the manual seed before this migration landed.
--
-- ⚠️ POST-UAT: TURN THIS OFF FOR PRODUCTION:
--   Production must NOT auto-seed test users. Either delete this migration
--   before the prod cutover or gate it behind a Spring profile (`@Profile("uat")`).
--   Tracked in HANDOFF.md follow-ups.
--
-- PIN HASHES:
--   The `$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW` value
--   is bcrypt of the string "1234" with cost=10. Used for every PIN-flow seed
--   user (TP_ADMIN, TP_TRANSPORT_MANAGER) for testing convenience. Per
--   HANDOFF.md §5#6, the COGNILOGISTIC_ADMIN seed users *also* get this PIN
--   hash — that's a known schema-gap workaround that contradicts the OTP-only
--   contract for admin. With the V20260508005 verify-otp branch, admins now
--   log in via the OTP-only path and the PIN_hash on their row is harmless.
-- =============================================================================


-- 1. Seed TP account (Bhoomihaar Express — pilot customer)
INSERT IGNORE INTO tp_accounts (
  id, name, gstin, address_line_1, city, state, pincode,
  plan, fleet_owner, account_status, account_status_updated_at
) VALUES (
  '11111111-1111-1111-1111-111111111111',
  'Bhoomihaar Express',
  '06AABCB1234A1Z5',
  'Sector 21, Industrial Area', 'Faridabad', 'Haryana', '121001',
  'ENTERPRISE', TRUE, 'APPROVED', CURRENT_TIMESTAMP
);


-- 2. Seed CogniLogistic admins (provisioned login-only — POST /auth/login, no signup/reset)
INSERT IGNORE INTO users (id, phone, name, role, onboarding_step) VALUES
  ('00000000-0000-0000-0000-000000000001', '+919999900001', 'Arjun Nair',   'COGNILOGISTIC_ADMIN', 3),
  ('00000000-0000-0000-0000-000000000002', '+919999900002', 'Sneha Kapoor', 'COGNILOGISTIC_ADMIN', 3);

-- Admin PIN_hash: bcrypt of "1234" for UAT login via POST /auth/login only.
INSERT IGNORE INTO auth_credentials (user_id, pin_hash) VALUES
  ('00000000-0000-0000-0000-000000000001', '$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW'),
  ('00000000-0000-0000-0000-000000000002', '$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW');


-- 3. Seed TP users (one TP_ADMIN + two TP_TRANSPORT_MANAGER under Bhoomihaar)
INSERT IGNORE INTO users (id, phone, name, role, tp_account_id, onboarding_step, whatsapp_number) VALUES
  ('22222222-2222-2222-2222-222222222221', '+919000000001', 'Vikram Singh',  'TP_ADMIN',             '11111111-1111-1111-1111-111111111111', 3, '+919000000001'),
  ('22222222-2222-2222-2222-222222222222', '+919000000002', 'Rahul Mehra',   'TP_TRANSPORT_MANAGER', '11111111-1111-1111-1111-111111111111', 3, '+919000000002'),
  ('22222222-2222-2222-2222-222222222223', '+919000000003', 'Anita Sharma',  'TP_TRANSPORT_MANAGER', '11111111-1111-1111-1111-111111111111', 3, '+919000000003');

-- PIN = "1234" for all three (bcrypt cost=10, see header comment)
INSERT IGNORE INTO auth_credentials (user_id, pin_hash) VALUES
  ('22222222-2222-2222-2222-222222222221', '$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW'),
  ('22222222-2222-2222-2222-222222222222', '$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW'),
  ('22222222-2222-2222-2222-222222222223', '$2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW');

-- Stamp Vikram as the TP account's primary owner. Use UPDATE (not INSERT IGNORE)
-- because the row exists from step 1; this is a follow-up FK fill-in once both
-- sides exist (avoids the chicken-and-egg between users.tp_account_id and
-- tp_accounts.primary_user_id).
UPDATE tp_accounts
   SET primary_user_id = '22222222-2222-2222-2222-222222222221'
 WHERE id = '11111111-1111-1111-1111-111111111111'
   AND primary_user_id IS NULL;


-- 4. Seed branch offices (5 across NCR)
INSERT IGNORE INTO offices (id, tp_account_id, name, code, city, state, pincode, is_active) VALUES
  ('33333333-3333-3333-3333-333333333301', '11111111-1111-1111-1111-111111111111', 'Faridabad 1',   'FBD1', 'Faridabad', 'Haryana',       '121001', TRUE),
  ('33333333-3333-3333-3333-333333333302', '11111111-1111-1111-1111-111111111111', 'Gurgaon',       'GRG',  'Gurgaon',   'Haryana',       '122001', TRUE),
  ('33333333-3333-3333-3333-333333333303', '11111111-1111-1111-1111-111111111111', 'Noida',         'NOI',  'Noida',     'Uttar Pradesh', '201301', TRUE),
  ('33333333-3333-3333-3333-333333333304', '11111111-1111-1111-1111-111111111111', 'Delhi Central', 'DEL',  'Delhi',     'Delhi',         '110001', TRUE),
  ('33333333-3333-3333-3333-333333333305', '11111111-1111-1111-1111-111111111111', 'Faridabad 2',   'FBD2', 'Faridabad', 'Haryana',       '121002', TRUE);


-- 5. Assign transport managers to offices (TM → office many-to-many)
INSERT IGNORE INTO user_office_assignments (user_id, office_id) VALUES
  ('22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333301'),
  ('22222222-2222-2222-2222-222222222223', '33333333-3333-3333-3333-333333333302');


-- 6. Seed demo customers (well-known Indian brand GSTINs for screenshot value)
INSERT IGNORE INTO customers (id, legal_name, phone, gstin, no_gst, is_shadow, created_by_tp_id, created_by_user_id) VALUES
  ('44444444-4444-4444-4444-444444444401', 'Tata Steel Ltd',           '+919222200001', '27AABCT3518Q1ZV', FALSE, FALSE, '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221'),
  ('44444444-4444-4444-4444-444444444402', 'Reliance Industries Ltd',  '+919222200002', '27AAACR5055K1Z5', FALSE, FALSE, '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221'),
  ('44444444-4444-4444-4444-444444444403', 'Dabur India Ltd',          '+919222200003', '07AAACD1391C1ZL', FALSE, FALSE, '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221');


-- 7. Default notification prefs for the TP users (so the dispatcher's auto-create doesn't fire)
INSERT IGNORE INTO notification_preferences (user_id) VALUES
  ('22222222-2222-2222-2222-222222222221'),
  ('22222222-2222-2222-2222-222222222222'),
  ('22222222-2222-2222-2222-222222222223');
