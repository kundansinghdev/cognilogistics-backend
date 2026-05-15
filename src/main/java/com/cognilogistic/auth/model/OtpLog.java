package com.cognilogistic.auth.model;

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

/**
 * JPA entity for the {@code otp_log} table — one row per OTP issuance.
 *
 * <p>Used by every OTP path:
 * <ul>
 *   <li>TP first-login signup ({@link OtpPurpose#FIRST_LOGIN})</li>
 *   <li>PIN-reset flow         ({@link OtpPurpose#PIN_RESET})</li>
 *   <li>Customer Portal sign-in ({@link OtpPurpose#FIRST_LOGIN} — same enum, different downstream)</li>
 *   <li>COGNILOGISTIC_ADMIN login ({@link OtpPurpose#FIRST_LOGIN})</li>
 * </ul>
 *
 * <p>⚠️ <strong>v5.0 schema only allows two purposes:</strong> FIRST_LOGIN and PIN_RESET.
 * Older drafts had UNLOCK and CUSTOMER_ACTIVATION; v5.0 dropped them. The lockout-clear
 * path uses the PIN-reset flow.
 *
 * <p><strong>OTP storage uses bcrypt</strong> ({@link #otpHash} VARCHAR(60)). Defense in depth:
 * even though OTPs are short-lived, bcrypt makes leaked DB dumps useless for replay.
 *
 * <p><strong>Single-use:</strong> {@link #verified} flips to TRUE on a successful verify.
 * Subsequent verify attempts on the same row return OTP_USED. Each new {@code send-otp}
 * call inserts a new row — the most-recent unverified row for {@code (phone, purpose)} is
 * the active OTP.
 *
 * <p><strong>10-minute TTL:</strong> {@link #expiresAt} is set at insert time to
 * {@code sentAt + 10 min}. Past this point, verification returns OTP_EXPIRED regardless of
 * the {@link #verified} flag.
 *
 * <p><strong>No FK to users.</strong> OTPs are issued by phone, BEFORE a {@code users} row
 * may exist (the TP first-login signup creates the user only AFTER OTP verification).
 * Phone is the join key.
 */
@Entity
@Table(name = "otp_log")
@Getter
@Setter
@NoArgsConstructor
public class OtpLog {

    /**
     * Primary key — server-generated UUID stored as CHAR(36).
     * Generated in {@code OtpService.send} via {@code UUID.randomUUID().toString()}.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * Phone the OTP was sent to. E.164 format. {@code VARCHAR(15)}. No FK to users —
     * see class-level Javadoc.
     */
    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    /**
     * BCrypt hash of the raw 6-digit OTP. {@code VARCHAR(60)} — fits the standard
     * {@code $2a$10$...} format. Verification: bcrypt-compare submitted OTP against this hash.
     * The raw OTP NEVER enters the database and is NEVER logged.
     */
    @Column(name = "otp_hash", nullable = false, length = 60)
    private String otpHash;

    /**
     * Why this OTP was issued. v5.0 only allows FIRST_LOGIN and PIN_RESET — see {@link OtpPurpose}.
     * The schema column is VARCHAR(20).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private OtpPurpose purpose;

    /**
     * When the OTP was generated and dispatched to the user. Set in service code (not via
     * DB default) so test fixtures with a fixed clock produce deterministic timestamps.
     */
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /**
     * Single-use marker. Flipped to TRUE on a successful verify. Subsequent verify attempts
     * on the same row return OTP_USED.
     *
     * <p>Replaces the legacy {@code consumed_at} timestamp column from older schema drafts —
     * v5.0 simplified to a boolean since the timestamp wasn't being used for anything beyond
     * "has this OTP been consumed?"
     */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    /**
     * Failed verify attempts against this OTP row. After {@code auth.otp.max-verify-attempts}
     * the row is treated as exhausted (same outcome as OTP_USED).
     */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /**
     * Validity cut-off — always {@link #sentAt} + 10 minutes. Past this instant, verify
     * returns OTP_EXPIRED regardless of {@link #verified}.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Returns {@code true} if this OTP has already been consumed by a successful verification.
     */
    public boolean isConsumed() {
        return verified;
    }

    /**
     * Returns {@code true} if the OTP's validity window has passed at the supplied instant.
     *
     * @param now the instant to compare against {@link #expiresAt}
     * @return {@code true} if expired
     */
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
