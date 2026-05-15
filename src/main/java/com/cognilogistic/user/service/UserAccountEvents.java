package com.cognilogistic.user.service;

/**
 * Namespace class for TP-account-lifecycle event records.
 *
 * <p>Mirrors {@link com.cognilogistic.order.service.OrderEvents} and
 * {@link com.cognilogistic.tender.service.TenderEvents}. These events are published
 * by user-module / admin-module flows when a TP account's status or plan changes;
 * the notification module's {@code EventListeners} consumes them to send the
 * "your account was approved / rejected / plan changed" messages.
 *
 * <p><strong>Producer status:</strong> not yet published — admin module isn't built.
 * Listeners are created here so the notification module's compilation is closed; the
 * publish-side wires up when admin module lands.
 *
 * <p>This class is not instantiable — use the inner record types directly.
 */
public final class UserAccountEvents {

    /**
     * Published when a COGNILOGISTIC_ADMIN approves a TP signup (account_status:
     * PENDING → APPROVED). The notification module sends SMS + In-App to the TP_ADMIN.
     *
     * @param tpAccountId   the approved TP account
     * @param tpAdminUserId the user id of the TP_ADMIN to notify
     * @param tpName        the TP's display name (used in the template body)
     * @param plan          the plan tier the admin set (BASIC / PREMIUM / ENTERPRISE)
     */
    public record TpAccountApproved(
            String tpAccountId,
            String tpAdminUserId,
            String tpName,
            String plan) {
    }

    /**
     * Published when a COGNILOGISTIC_ADMIN rejects a TP signup
     * (account_status: PENDING → REJECTED). Notification: SMS + In-App.
     *
     * @param tpAccountId   the rejected TP account
     * @param tpAdminUserId the user id of the TP_ADMIN to notify
     * @param reason        free-text rejection reason for the message body (may be {@code null})
     */
    public record TpAccountRejected(
            String tpAccountId,
            String tpAdminUserId,
            String reason) {
    }

    /**
     * Published when a COGNILOGISTIC_ADMIN changes a TP's plan tier.
     * Notification: SMS + In-App.
     *
     * @param tpAccountId   the TP whose plan changed
     * @param tpAdminUserId the user id of the TP_ADMIN to notify
     * @param oldPlan       the prior plan label (e.g. {@code "BASIC"})
     * @param newPlan       the new plan label (e.g. {@code "PREMIUM"})
     */
    public record TpPlanChanged(
            String tpAccountId,
            String tpAdminUserId,
            String oldPlan,
            String newPlan) {
    }

    private UserAccountEvents() {}
}
