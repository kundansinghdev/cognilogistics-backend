package com.cognilogistic.platform.admin;

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
 * JPA entity for the {@code impersonation_audit_log} table — append-only record of
 * every COGNILOGISTIC_ADMIN "Log in as" session.
 *
 * <p><strong>Lifecycle:</strong>
 * <ul>
 *   <li>{@code POST /admin/impersonate} writes a row with
 *       {@code session_started_at} = now, {@code session_ended_at} = NULL.</li>
 *   <li>Audit hooks during the session increment {@code actions_performed}
 *       on every mutation that carries an {@code imp} JWT claim.</li>
 *   <li>{@code POST /admin/impersonate/{sessionId}/exit} sets {@code session_ended_at}.</li>
 * </ul>
 *
 * <p>Retention: append-only and never deleted. The table grows roughly with admin
 * activity (low cardinality vs business tables).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 277–297;
 * created by migration {@code V20260508004__impersonation_audit_log.sql}.
 */
@Entity
@Table(name = "impersonation_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class ImpersonationAuditLog {

    /** CHAR(36) UUID. Doubles as the {@code sessionId} the FE passes to the exit endpoint. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** The COGNILOGISTIC_ADMIN who initiated the session. */
    @Column(name = "admin_user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String adminUserId;

    /** Denormalised admin name — keeps audit reads fast and informative even if the user is later renamed. */
    @Column(name = "admin_name", length = 255)
    private String adminName;

    /** {@code TP} / {@code PARTNER} / {@code CUSTOMER} — which account type was entered. */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** Set for TP impersonation; FK to {@code tp_accounts.id}. */
    @Column(name = "target_tp_account_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String targetTpAccountId;

    /** Set for PARTNER / CUSTOMER impersonation; FK to {@code users.id}. */
    @Column(name = "target_user_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String targetUserId;

    /** Denormalised display name of the org or user being impersonated. */
    @Column(name = "target_name", length = 255)
    private String targetName;

    @Column(name = "session_started_at", nullable = false)
    private Instant sessionStartedAt;

    /** {@code null} while the session is still active. */
    @Column(name = "session_ended_at")
    private Instant sessionEndedAt;

    /** Counter — incremented by audit hooks on every mutation during the session. */
    @Column(name = "actions_performed", nullable = false)
    private int actionsPerformed;

    /** Optional reason text the admin entered before starting (compliance / support trail). */
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
