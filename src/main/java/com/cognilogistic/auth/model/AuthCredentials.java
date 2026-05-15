package com.cognilogistic.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * JPA entity for the {@code auth_credentials} table — stores the bcrypt-hashed PIN and
 * brute-force lockout state for users who authenticate via PIN.
 *
 * <p><strong>One row per user; PK is {@code user_id} directly.</strong> There is no
 * surrogate id column. The schema deliberately collapses the join — see schema.sql v5.0
 * line 86.
 *
 * <p><strong>⚠️ Rows do NOT exist for CUSTOMER portal users.</strong> CUSTOMER
 * authenticates via OTP every session. Provisioned COGNILOGISTIC_ADMIN users have a PIN
 * row assigned at insert time and sign in only via {@code POST /auth/login}. Code that
 * joins {@link User} to AuthCredentials must use LEFT JOIN and handle absent rows.
 *
 * <p><strong>Brute-force lockout:</strong>
 * <ul>
 *   <li>{@link #failedAttempts} resets to 0 on every successful login.</li>
 *   <li>When it reaches 5 (BR-AUTH-06), {@link #lockedUntil} is set to {@code now + 30min}
 *       and {@link #failedAttempts} resets to 0. Subsequent login attempts return
 *       423 ACCOUNT_LOCKED until {@code lockedUntil} passes.</li>
 *   <li>The PIN-reset flow (auth.md §3.3) clears the lockout immediately.</li>
 * </ul>
 *
 * <p><strong>Why no surrogate id?</strong> One row per user — the user_id IS the natural
 * primary key. Adding a surrogate would just consume an index slot. The schema's choice
 * (PK on user_id) makes the 1:1 relationship to {@link User} structurally enforced.
 *
 * <p>Does NOT extend {@link com.cognilogistic.platform.BaseEntity} because {@code BaseEntity}
 * uses a different PK structure. We declare {@code created_at} / {@code updated_at} explicitly
 * via {@link AuditingEntityListener} below.
 */
@Entity
@Table(name = "auth_credentials")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AuthCredentials {

    /**
     * Primary key AND foreign key to {@link User#id}. CHAR(36) UUID string.
     *
     * <p>The {@code @Id} on {@code user_id} encodes the v5.0 design choice that this
     * table has no surrogate id — the user is the row.
     */
    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * BCrypt hash of the user's 4-digit PIN. Cost factor 10. Never store the raw PIN;
     * never log it. Schema column is {@code VARCHAR(60)} — exactly fits the standard
     * bcrypt format ({@code $2a$10$...}).
     */
    @Column(name = "pin_hash", nullable = false, length = 60)
    private String pinHash;

    /**
     * Number of consecutive failed PIN attempts since the last successful login.
     * Increments on each wrong-PIN attempt; resets to 0 on a successful login or PIN reset.
     * BR-AUTH-06.
     */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /**
     * If non-null and in the future, the account is locked out — login returns
     * 423 ACCOUNT_LOCKED with this timestamp in the error details. Cleared (set to null)
     * on successful login or PIN reset. BR-AUTH-07.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Server-set on row INSERT (Spring Data JPA auditing).
     * The DB column has {@code DEFAULT CURRENT_TIMESTAMP} as a safety net for any direct INSERTs.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Server-updated on every save. Useful in support tooling: "when was the PIN or lockout
     * state last touched?"
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Returns {@code true} if the account is currently locked at the supplied instant.
     * Used by {@code AuthService.login} to short-circuit before checking the PIN hash.
     *
     * @param now the instant to compare against {@link #lockedUntil} (typically {@code Instant.now()})
     * @return {@code true} if the lockout window has not yet elapsed
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
