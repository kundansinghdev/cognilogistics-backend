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
 * JPA entity for the {@code device_registrations} table — Azure Notification Hubs
 * routing list per (user, device).
 *
 * <p><strong>Post-UAT.</strong> The entity exists today so the column shape is fixed
 * before the push channel goes live, but the {@code PushChannel} adapter is a no-op
 * in UAT (push_enabled defaults to false in {@link NotificationPreference}). When
 * push lands, mobile clients call {@code POST /api/v1/me/devices} on first launch
 * and the resulting NH handle goes into this table.
 *
 * <p>Re-registration of an existing device updates the row's
 * {@link #notificationHubHandle} in place rather than creating a duplicate — this is
 * enforced by the DB-level UNIQUE on {@code (user_id, device_id)}.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 673–684.
 */
@Entity
@Table(name = "device_registrations")
@Getter
@Setter
@NoArgsConstructor
public class DeviceRegistration {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** The user who owns this device. CASCADE on user delete. */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Stable client-generated device identifier. Same shape as
     * {@code refresh_tokens.device_id} (auth module) — an opaque string the mobile
     * app generates on first install. UNIQUE on {@code (user_id, device_id)} — see
     * class-level Javadoc.
     */
    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    /** Mobile platform — IOS or ANDROID. Drives the NH platform-specific payload format. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private DevicePlatform platform;

    /**
     * Opaque handle returned by Azure Notification Hubs when the device registers.
     * NH handles are long base64 strings; the column is VARCHAR(500) to fit them
     * with headroom.
     */
    @Column(name = "notification_hub_handle", nullable = false, length = 500)
    private String notificationHubHandle;

    /**
     * When the device first registered (or re-registered, since the row is updated
     * in place on re-registration). Set in code so tests with a fixed Clock are
     * deterministic; the DB has {@code DEFAULT CURRENT_TIMESTAMP} as a safety net.
     */
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
