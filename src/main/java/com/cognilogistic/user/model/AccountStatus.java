package com.cognilogistic.user.model;

/**
 * Lifecycle of a Transport Provider account from the platform's perspective.
 *
 * <p>Stored as the enum's {@code name()} string in the {@code tp_accounts.account_status}
 * column (VARCHAR(20)). Drives the cross-cutting authorisation gate that every business
 * controller invokes on every state-changing request — see user.md §3.1 (BR-PLN-02).
 *
 * <p><strong>Lifecycle (admin.md §3.2):</strong>
 * <pre>
 *               (signup)
 *                   │
 *                   ▼
 *               PENDING ─────► APPROVED   ◄─── (Platform Admin reviews)
 *                   │              │
 *                   └─────► REJECTED ◄────── (Platform Admin declines)
 *                                  ▲
 *                                  │ (re-approval allowed)
 *                                  ▼
 *                              APPROVED
 * </pre>
 *
 * <p><strong>Effects on access:</strong>
 * <ul>
 *   <li>{@link #PENDING} — login succeeds, but every business-action endpoint
 *       (create order, post tender, confirm fleet, …) returns
 *       {@code 403 ACCOUNT_PENDING_APPROVAL}. Read endpoints stay open so the user
 *       can see an empty-state banner.</li>
 *   <li>{@link #APPROVED} — full access per the TP's plan tier.</li>
 *   <li>{@link #REJECTED} — login succeeds (so the user sees a "your application was
 *       declined" screen with the rejection reason), but every business endpoint returns
 *       {@code 403 ACCOUNT_REJECTED}.</li>
 * </ul>
 */
public enum AccountStatus {

    /** New TP signup — awaiting CogniLogistic Platform Admin review. */
    PENDING,

    /** Reviewed and accepted — full plan-based access unlocked. */
    APPROVED,

    /** Reviewed and declined — login allowed, business actions blocked. */
    REJECTED
}
