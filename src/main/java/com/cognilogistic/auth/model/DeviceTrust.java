package com.cognilogistic.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity for the {@code device_trust} table — a marker recording which (user, device)
 * pairs the user has confirmed via a fresh PIN entry.
 *
 * <p><strong>Purpose (post-UAT feature):</strong> the 15-day "trust this device" UX where
 * a TP user on a known device skips PIN re-entry inside the trust window. On successful
 * PIN entry, the service upserts this row with {@code trustedAt = now}; on subsequent
 * logins the service checks whether {@code trustedAt + 15 days > now} and, if so, issues
 * tokens silently.
 *
 * <p><strong>v5.0 simplification:</strong> Earlier drafts had {@code trusted_until} and
 * {@code last_pin_at} columns to encode the window explicitly. v5.0 dropped them — the
 * trust window is computed at the application layer ({@code trustedAt + 15 days}), keeping
 * the table tiny and the rule easy to change without a schema migration.
 *
 * <p><strong>Roles:</strong> only PIN-modality users (TP_ADMIN, TP_TRANSPORT_MANAGER,
 * PARTNER_TP, provisioned COGNILOGISTIC_ADMIN) ever appear here. CUSTOMER portal users
 * re-OTP every session; device trust doesn't apply to them.
 */
@Entity
@Table(name = "device_trust")
@Getter
@Setter
@NoArgsConstructor
public class DeviceTrust {

    /**
     * Primary key — server-generated UUID. CHAR(36).
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The user who trusts the device. CHAR(36) UUID string. The DB has a CASCADE delete
     * so deleting the user wipes their trust markers.
     */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Device identifier — same shape as {@code refresh_tokens.device_id}. The pair
     * {@code (user_id, device_id)} is the unique trust granularity (UNIQUE constraint at the DB).
     */
    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    /**
     * When the device was last confirmed by a fresh PIN entry. Application code computes
     * the trust-window expiry as {@code trustedAt + 15 days}.
     */
    @Column(name = "trusted_at", nullable = false)
    private Instant trustedAt;

    /**
     * Generates and assigns a UUID to {@link #id} if not already set. Convenience for service
     * code so callers don't have to remember to call {@link UUID#randomUUID()}.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
