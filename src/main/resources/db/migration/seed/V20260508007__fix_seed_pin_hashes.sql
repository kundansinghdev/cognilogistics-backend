-- =============================================================================
-- Migration:  V20260508007__fix_seed_pin_hashes.sql
-- Module:     seed (UAT-only)
-- Date:       2026-05-08
-- Author:     CogniLogistic Platform Team
--
-- WHY THIS EXISTS:
--   The PIN bcrypt hash baked into V20260508006 (and historically in
--   `database/schema.sql` at FinalVersion lines 814–827) was a placeholder:
--     $2a$10$7QYNMo3bO/HLn3mdLcGNVexg1rPI4jq8xJ5SFKSnt3PQ1L7cCN5fW
--   It is NOT a real bcrypt of the documented seed PIN "1234". As a result
--   `/auth/login` returned 401 INVALID_PIN for every seeded user — including
--   the admin login flow the FE team needed for impersonation testing.
--
--   This migration overwrites every seeded user's pin_hash with a known-good
--   bcrypt of "1234" (cost=10) generated and verified separately. Idempotent —
--   safe to re-run.
--
-- AFFECTED USERS (all five share the seed PIN "1234"):
--   00000000-...-000001  Arjun Nair        COGNILOGISTIC_ADMIN
--   00000000-...-000002  Sneha Kapoor      COGNILOGISTIC_ADMIN
--   22222222-...-222221  Vikram Singh      TP_ADMIN
--   22222222-...-222222  Rahul Mehra       TP_TRANSPORT_MANAGER
--   22222222-...-222223  Anita Sharma      TP_TRANSPORT_MANAGER
--
-- NOTE on V20260508006: that migration's pin_hash was the placeholder. We do
--   NOT edit it (Flyway records its checksum at first apply; editing would
--   break validation on existing DBs). Instead we layer this UPDATE migration
--   on top — fresh DBs get the placeholder via V20260508006 then immediately
--   get it overwritten via V20260508007 (same boot, same Flyway run).
--
-- BCRYPT VARIANT NOTE:
--   The hash below uses the $2b$ prefix (modern bcrypt). Spring Security's
--   BCryptPasswordEncoder.matches() accepts both $2a$ and $2b$, so this works
--   identically.
-- =============================================================================

UPDATE auth_credentials
   SET pin_hash = '$2b$10$CrW.VdPNR/NsD96datdHjOejWHUStKZPu9cEanoBCS4y0PDpX.Erq',
       failed_attempts = 0,
       locked_until = NULL
 WHERE user_id IN (
   '00000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002',
   '22222222-2222-2222-2222-222222222221',
   '22222222-2222-2222-2222-222222222222',
   '22222222-2222-2222-2222-222222222223'
 );
