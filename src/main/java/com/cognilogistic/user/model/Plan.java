package com.cognilogistic.user.model;

/**
 * The commercial plan a Transport Provider is on. Drives the per-module access gate
 * via {@code plan_access_rules} (see user.md §3.2, BR-PLN-01).
 *
 * <p>Stored as the enum's {@code name()} string in the {@code tp_accounts.plan} column
 * (VARCHAR(20)). Set ONLY by COGNILOGISTIC_ADMIN through the Admin Portal — TP users
 * cannot self-upgrade (BR-PLN-04). The audit trail lives in the
 * {@code tp_accounts.plan_set_at / plan_set_by} columns.
 *
 * <p><strong>Module access matrix (user.md §3.2):</strong>
 * <pre>
 *   Module          │ BASIC │ PREMIUM │ ENTERPRISE
 *   ────────────────┼───────┼─────────┼────────────
 *   Tender          │  ✓ *  │   ✓     │     ✓
 *   Order           │  ✗    │   ✓     │     ✓
 *   Branch Office   │  ✗    │   ✓     │     ✓
 *   Company Master  │  ✗    │   ✓     │     ✓
 *   Fleet           │  ✗    │   ✓     │     ✓
 *   Reports         │  ✗    │   ✗     │     ✓ (post-UAT)
 *
 *   * BASIC capped at 5 tenders / month (BR-PLN-03)
 * </pre>
 *
 * <p>The matrix is enforced at runtime by reading {@code plan_access_rules} (DB-driven,
 * not hardcoded — see user.md §3.2). If ops want to grant Order access to BASIC, they
 * just UPDATE the {@code plan_access_rules.min_plan} column for the ORDER module. No
 * code deploy required.
 */
public enum Plan {

    /**
     * Default for new signups. Tender module only, capped at 5 tenders/month
     * (BR-PLN-03 enforced via {@code plan_usage} counters).
     */
    BASIC,

    /**
     * Tender (unlimited) + Order + Branch Office + Company Master + Fleet.
     * The standard paid tier.
     */
    PREMIUM,

    /**
     * All of {@link #PREMIUM} plus the Reports module (post-UAT). The top tier.
     */
    ENTERPRISE
}
