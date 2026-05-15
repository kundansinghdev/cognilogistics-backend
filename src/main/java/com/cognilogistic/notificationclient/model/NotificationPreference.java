package com.cognilogistic.notificationclient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code notification_preferences} table — per-user channel opt-in.
 *
 * <p>Exactly one row per user. Created automatically the first time the user logs in
 * (or upon TP signup) so the {@link com.cognilogistic.notificationclient.service.NotificationService}
 * dispatcher can consult it without null checks. Defaults: SMS=true, WhatsApp=true,
 * Push=false (post-UAT infra).
 *
 * <p><strong>Email is intentionally NOT a column on this entity.</strong> v5.0 schema
 * dropped the {@code email_enabled} field because the pilot has no email-based auth or
 * outbound. If email comes back, the schema needs the column first; until then the
 * dispatcher hard-codes "skip EMAIL". See notification.md §6.1.
 *
 * <p><strong>No audit timestamps.</strong> v5.0 schema does not have {@code created_at} /
 * {@code updated_at} on this table, so this entity does NOT extend {@link com.cognilogistic.platform.BaseEntity}.
 * If we need history later we can add the columns and a {@code @MappedSuperclass}.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 652–659.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    /**
     * Primary key — the user this prefs row belongs to. The {@code user_id} column
     * is itself the PK (one row per user) and the FK to {@code users.id}; deleting
     * a user cascades to their prefs.
     */
    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /** Whether the user accepts SMS notifications. Default TRUE on row creation. */
    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = true;

    /** Whether the user accepts WhatsApp template messages. Default TRUE. */
    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled = true;

    /**
     * Whether the user accepts mobile push notifications. Default FALSE — the push
     * channel (Azure Notification Hubs) is post-UAT, so opt-in is meaningless until
     * that infra is live.
     */
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    /**
     * Convenience factory for creating a fresh preferences row at user creation time
     * with the platform defaults baked in. Call sites (auth's setup-pin / first-login
     * hook) just need to pass the user's id.
     *
     * @param userId the user's UUID (CHAR(36))
     * @return an unsaved {@code NotificationPreference} ready to persist
     */
    public static NotificationPreference defaultsFor(String userId) {
        NotificationPreference p = new NotificationPreference();
        p.userId = userId;
        p.smsEnabled = true;
        p.whatsappEnabled = true;
        p.pushEnabled = false;
        return p;
    }
}
