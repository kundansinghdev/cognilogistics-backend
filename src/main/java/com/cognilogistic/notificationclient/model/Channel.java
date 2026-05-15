package com.cognilogistic.notificationclient.model;

/**
 * Outbound channel through which a notification can be delivered to a user.
 *
 * <p>Stored as the enum's {@code name()} string in the
 * {@code notification_log.channel} VARCHAR(20) column. The schema column is free-text
 * so adding a new channel does not require a migration; this enum is the application's
 * authoritative list and {@link #valueOf(String)} is used to round-trip rows back into
 * Java.
 *
 * <p><strong>UAT scope (notification.md §1):</strong>
 * <ul>
 *   <li>{@link #SMS} — real Twilio SMS (mocked by default in dev/UAT).</li>
 *   <li>{@link #WHATSAPP} — template-only generation, no BSP integration. We render
 *       the Hindi template + a {@code wa.me} link and the TP user forwards manually.
 *       Logged with {@link NotificationStatus#GENERATED}.</li>
 *   <li>{@link #IN_APP} — always on; the in-app feed is the user's notifications list
 *       served by {@code GET /api/v1/notifications}.</li>
 *   <li>{@link #PUSH} — Azure Notification Hubs; post-UAT, off in pilot.</li>
 *   <li>{@link #EMAIL} — no email auth in pilot, so always disabled. Kept for
 *       forward-compatibility.</li>
 * </ul>
 *
 * <p><strong>Why a Java enum and not a DB enum / lookup table:</strong> the channel set
 * changes rarely (one new entry every couple of years) and rendering / dispatch logic
 * is keyed on the enum at compile time anyway, so a runtime lookup table would buy us
 * no flexibility.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 661–671
 * (column {@code notification_log.channel}).
 */
public enum Channel {

    /** Short Message Service via Twilio. UAT mocks; production calls the Twilio Messages API. */
    SMS,

    /**
     * WhatsApp delivery. In the pilot we generate a template + {@code wa.me} link
     * and the TP user forwards it manually; no BSP. Post-UAT this becomes a real
     * BSP integration (Gupshup / Karix etc.).
     */
    WHATSAPP,

    /** Mobile push via Azure Notification Hubs. Disabled in UAT. */
    PUSH,

    /** SMTP email. No email-based auth in the pilot, so this channel is permanently disabled. */
    EMAIL,

    /**
     * In-app notification — surfaces in the mobile / web app's notification feed
     * via {@code GET /api/v1/notifications}. There is no out-of-band delivery;
     * we simply persist a row in {@code notification_log} that the client polls for.
     */
    IN_APP
}
