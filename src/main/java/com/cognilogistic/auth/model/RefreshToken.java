package com.cognilogistic.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code refresh_tokens} table — DB-backed refresh tokens that make
 * stateless JWT access tokens revocable (DD-03).
 *
 * <p><strong>Lifecycle (auth.md §3.4):</strong>
 * <ol>
 *   <li>Issued on successful login (PIN or OTP). The raw 32-byte token is sent to the
 *       client; only its SHA-256 hex digest is persisted in {@link #tokenHash}.</li>
 *   <li>On {@code POST /auth/refresh}: the row is marked revoked (single-use rotation)
 *       and a new row is inserted with a fresh raw token.</li>
 *   <li>On logout: the supplied token's row is marked revoked.</li>
 *   <li>On PIN reset: ALL rows for the user are marked revoked (lost-device protection).</li>
 * </ol>
 *
 * <p><strong>One active session per device.</strong> The DB has a UNIQUE constraint on
 * {@code (user_id, device_id)} — the application layer revokes the previous row before
 * inserting a new one for the same device.
 *
 * <p><strong>SHA-256 (not bcrypt) for {@link #tokenHash}.</strong> Refresh tokens are
 * already 32 bytes of CSPRNG output — high-entropy. bcrypt's cost adds latency without
 * meaningful brute-force resistance against random secrets.
 *
 * <p>Does NOT extend {@link com.cognilogistic.platform.BaseEntity} because the schema
 * has only {@code created_at} (no {@code updated_at}). We declare it directly here.
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    /**
     * Primary key — server-generated UUID stored as CHAR(36).
     * Generated in {@code TokenService.issueRefreshToken} via {@link UUID#randomUUID()}.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The user this token belongs to. CHAR(36) UUID string. The DB has a CASCADE delete
     * so deleting the user wipes all their sessions.
     */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Client-supplied stable device identifier. The pair {@code (user_id, device_id)} is
     * the unique session granularity. {@code VARCHAR(255)}.
     */
    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    /**
     * Whether this session is from a web browser or a mobile app. Drives the access-token
     * TTL on issuance: 15 min for WEB, 1 hr for MOBILE. Stored on the refresh row so
     * {@code /auth/refresh} can re-issue with the correct TTL without the client
     * re-supplying device_type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 10)
    private DeviceType deviceType;

    /**
     * SHA-256 hex digest of the raw 32-byte token. {@code CHAR(64)} — exactly 64 hex chars.
     * The raw token is computed in service code, sent to the client once, and never
     * persisted. On {@code /auth/refresh} the server hashes the supplied raw token and
     * looks up by this column.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.CHAR)} tells Hibernate this is a fixed-length
     * {@code CHAR} column (not the default VARCHAR mapping for {@code String}). Without
     * it, {@code ddl-auto: validate} fails at boot because the schema column is CHAR
     * and the entity would otherwise expect VARCHAR.
     */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    /**
     * Hard cut-off — beyond this instant the row is no longer eligible for refresh,
     * regardless of {@link #revokedAt}. Set at insert time to {@code now + 30 days}
     * (configurable via {@code auth.jwt.refreshTtlDays}).
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Set when the token is consumed (logout, refresh-rotation, or revoke-all). NULL = active.
     * Once set, the row cannot be used again — single-use rotation guarantees that a stolen
     * token, when used, immediately invalidates the legitimate device's session and forces
     * a re-login (which is the desired theft-detection behaviour).
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Returns {@code true} if this token can still be used to refresh — not revoked and
     * not expired.
     *
     * @param now the instant to compare against (typically {@code Instant.now()})
     * @return {@code true} if both {@link #revokedAt} is null and {@link #expiresAt} is in the future
     */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
