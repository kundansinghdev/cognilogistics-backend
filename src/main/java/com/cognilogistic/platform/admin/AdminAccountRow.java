package com.cognilogistic.platform.admin;

/**
 * Cross-tenant aggregate row returned by {@code GET /api/v1/admin/accounts}
 * (BACKEND_GAPS §7).
 *
 * <p>One DTO carries three different account types — {@link #type} discriminates:
 * <ul>
 *   <li>{@code TP} — populates {@link #tpAccountId}, {@link #orgName},
 *       {@link #plan}, {@link #accountStatus}; {@link #userId} / {@link #userName}
 *       point at the TP_ADMIN.</li>
 *   <li>{@code PARTNER} — populates {@link #userId}, {@link #userName} (company name
 *       from the partner profile); other fields generally null.</li>
 *   <li>{@code CUSTOMER} — populates {@link #userId} (or customer id), {@link #userName}.
 *       {@link #isShadow} is true for placeholder customers that haven't activated
 *       portal access yet.</li>
 * </ul>
 *
 * @param id            row id — used as the {@code accountId} path-param on
 *                      status / plan / impersonate calls. For TPs this is the
 *                      tp_account_id; for PARTNER / CUSTOMER it's the user_id.
 * @param type          {@code "TP"} / {@code "PARTNER"} / {@code "CUSTOMER"}
 * @param tpAccountId   tp_account UUID (TP rows only)
 * @param orgName       organisation name (TP rows only)
 * @param userId        user UUID for PARTNER / CUSTOMER rows; the primary admin's
 *                      user id for TP rows
 * @param userName      display name to render
 * @param plan          plan tier — {@code "BASIC"} / {@code "PREMIUM"} / {@code "ENTERPRISE"}
 *                      (TP rows only)
 * @param accountStatus {@code "PENDING"} / {@code "APPROVED"} / {@code "REJECTED"}
 *                      (TP rows only)
 * @param isShadow      true for shadow customer placeholders (CUSTOMER rows)
 * @param orderCount    number of orders linked to this account; meaning depends
 *                      on type (TP: orders created in their tenancy; CUSTOMER:
 *                      orders for them; PARTNER: 0 in R6, future iterations
 *                      can use awarded-tender count)
 */
public record AdminAccountRow(
        String id,
        String type,
        String tpAccountId,
        String orgName,
        String userId,
        String userName,
        String plan,
        String accountStatus,
        boolean isShadow,
        long orderCount
) {}
