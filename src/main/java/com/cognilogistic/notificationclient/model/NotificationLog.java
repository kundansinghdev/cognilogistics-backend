package com.cognilogistic.notificationclient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code notification_log} table — append-only record of every
 * notification dispatch (sent, failed, skipped, generated).
 *
 * <p>Two reads matter:
 * <ul>
 *   <li>The user's in-app feed — {@code WHERE user_id = ? AND channel = 'IN_APP'
 *       ORDER BY sent_at DESC} — served by {@code GET /api/v1/notifications}.</li>
 *   <li>Support / audit lookups — "did we send X to phone Y on date Z?" — used by
 *       the COGNILOGISTIC_ADMIN portal post-UAT.</li>
 * </ul>
 *
 * <p><strong>Schema gaps the application works around (notification.md §10):</strong>
 * <ul>
 *   <li><strong>No {@code event_id} column.</strong> v5.0 dropped it, so the
 *       (event_id, channel, user_id) idempotency UNIQUE that older drafts had no
 *       longer exists. Dedup happens in
 *       {@link com.cognilogistic.notificationclient.service.NotificationService}'s
 *       in-memory cache. UAT-acceptable; horizontal scaling will need either the
 *       column back or Redis-backed dedup.</li>
 *   <li><strong>No {@code payload_json} column.</strong> For WhatsApp template
 *       generation we need to persist the rendered text + the {@code wa.me} link.
 *       We squeeze that JSON into the {@code template} VARCHAR(100) column —
 *       which is short, but the link + template fits with care. If a single
 *       template overflows, see notification.md §10.3 for the schema-add fix.</li>
 *   <li><strong>{@code status} enum mismatch.</strong> Schema lists SENT/FAILED/PENDING;
 *       application also writes SKIPPED_PREFERENCE and GENERATED. Column is
 *       VARCHAR(20) — no DB-side check — so this is fine.</li>
 *   <li><strong>No {@code read_at} column.</strong> "Read / unread" state is
 *       client-side (mobile / web localStorage) for UAT.</li>
 * </ul>
 *
 * <p><strong>No JPA auditing.</strong> The schema only has {@code sent_at} (set once at
 * insert), so this entity does not extend {@link com.cognilogistic.platform.BaseEntity}.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 661–671.
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The recipient user. CASCADE — deleting a user wipes their notification history
     * (FK constraint in V15 migration).
     */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Which channel this row corresponds to. {@link Channel#IN_APP} rows are the
     * user-facing feed; the others are out-of-band send records.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    /**
     * Template name used (e.g. {@code "ORDER_DELIVERED_HI"}), or — for WhatsApp
     * GENERATED rows — a JSON blob containing the rendered text + {@code wa.me} link
     * (see class-level Javadoc for the workaround). NULL for ad-hoc messages with
     * no associated template.
     */
    @Column(name = "template", length = 100)
    private String template;

    /**
     * Outcome of the dispatch. {@link NotificationStatus#SENT}, {@link NotificationStatus#FAILED},
     * {@link NotificationStatus#PENDING}, {@link NotificationStatus#SKIPPED_PREFERENCE}, or
     * {@link NotificationStatus#GENERATED}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    /**
     * When the dispatch was attempted. Set in code (not via DB default) so unit tests
     * with a fixed Clock can produce deterministic timestamps. The DB column has
     * {@code DEFAULT CURRENT_TIMESTAMP} as a safety net for any rows inserted via
     * raw SQL.
     */
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    /**
     * Free-text error description when {@link #status} is {@link NotificationStatus#FAILED}.
     * NULL for any other status. Helps support diagnose Twilio / NH errors without
     * digging through application logs.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
