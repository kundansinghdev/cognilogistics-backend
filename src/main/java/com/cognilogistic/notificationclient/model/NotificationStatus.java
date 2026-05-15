package com.cognilogistic.notificationclient.model;

/**
 * Outcome of a notification dispatch attempt.
 *
 * <p>Stored as the enum's {@code name()} string in the {@code notification_log.status}
 * VARCHAR(20) column. The schema does not enforce a CHECK constraint on this set —
 * v5.0 keeps it free-text so we can add new outcomes without a migration. This enum
 * is the application's authoritative list of values that should appear in that column.
 *
 * <p><strong>Why "skip" and "generated" are recorded as their own statuses:</strong>
 * for compliance auditing the system needs to prove "we tried to deliver this
 * notification" even when nothing was actually sent — a customer complaint of "I
 * didn't get the message" is answered by these rows. {@link #SKIPPED_PREFERENCE}
 * proves the user opted out; {@link #GENERATED} proves we produced a wa.me link and
 * the user (the TP forwarding it) was the manual delivery step.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 661–671
 * (column {@code notification_log.status}).
 */
public enum NotificationStatus {

    /**
     * The notification was successfully accepted by the downstream channel
     * (Twilio returned 200, in-app row was inserted, push provider acknowledged).
     */
    SENT,

    /**
     * The downstream channel returned an error or was unreachable. Detail is recorded
     * in {@code notification_log.error_message}. Failed sends do not auto-retry in
     * UAT; ops can re-trigger if needed.
     */
    FAILED,

    /**
     * The dispatch is queued / in-flight. Not heavily used in UAT (sends are synchronous
     * within an async listener), but reserved for the post-UAT path where Azure
     * Notification Hubs returns "accepted, will deliver later".
     */
    PENDING,

    /**
     * The user has opted out of this channel in {@code notification_preferences}.
     * Recorded as a row so auditors can prove the system attempted delivery and
     * the user's preference blocked it. {@link Channel#IN_APP} is never skipped —
     * it has no preference toggle.
     */
    SKIPPED_PREFERENCE,

    /**
     * Specific to {@link Channel#WHATSAPP} in the pilot: we generated a Hindi template
     * + a {@code wa.me} URL but the actual delivery is the TP user manually forwarding
     * via WhatsApp. The link / template text is stored in
     * {@code notification_log.template} as JSON for the in-app feed to render.
     */
    GENERATED
}
