package com.cognilogistic.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity for the {@code audit_logs} table — the cross-cutting audit trail of every
 * mutation performed on a tracked business entity (orders, tenders, offices, tp_accounts,
 * users, companies, partner_tp_profiles, etc.).
 *
 * <p>This is the sink for the platform's audit aspect (post-UAT) and the canonical record
 * relied on by support, compliance (DPDP 7-year retention), and the Admin Portal's
 * impersonation forensics. The table is <strong>append-only</strong>: rows are inserted
 * but never updated or deleted by application code. Retention is handled by a periodic
 * archival job, not by row-level updates.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 35–51.
 * The schema deliberately has no foreign-key constraints from this table to {@code users}
 * or to any other entity — referenced rows may be deleted while audit records must survive.
 * Treat {@link #actorUserId}, {@link #entityId}, and {@link #impersonatedByUserId} as
 * denormalised string references; do not configure a JPA {@code @ManyToOne}.
 *
 * <p><strong>Why a separate {@code actor_name} column:</strong> snapshots the user's name
 * at write time so the audit row stays meaningful even if the user is later renamed or
 * deleted. Reconstructing names by joining to {@code users} on read would be lossy.
 *
 * <p><strong>Impersonation context:</strong> when a CogniLogistic Platform Admin
 * (role {@code COGNILOGISTIC_ADMIN}) is impersonating a tenant via the Admin Portal
 * "Log in as" feature, every write performed during the impersonation session populates
 * {@link #impersonatedByUserId} with the admin's user id. The {@link #actorUserId} is
 * the impersonated tenant user — i.e., the identity whose tokens were minted. This split
 * lets ops query "which writes did Admin X perform across the platform?" while keeping
 * the regular tenant-side audit story intact. See admin.md §3.6.
 *
 * <p><strong>UUID storage:</strong> the schema uses {@code CHAR(36)} for all id columns.
 * We model these as Java {@link String} rather than {@link java.util.UUID} so the
 * stored format is unambiguous and obvious to any reader. The
 * {@link com.cognilogistic.platform.audit.AuditService} is responsible for generating
 * the id via {@link java.util.UUID#randomUUID()} at insert time.
 *
 * <p>This entity does NOT extend {@link com.cognilogistic.platform.BaseEntity} because
 * audit rows are never updated — there is no {@code updated_at} column and the
 * {@code created_at} field is hand-rolled below.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_entity",        columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_actor",         columnList = "actor_user_id"),
        @Index(name = "idx_audit_impersonation", columnList = "impersonated_by_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    /**
     * Primary key — UUID stored as CHAR(36).
     * Generated server-side at insert time by
     * {@link com.cognilogistic.platform.audit.AuditService#append}.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The database table name of the entity that was mutated, e.g. {@code "orders"} or
     * {@code "tp_accounts"}. We use the table name (not the JPA class name) so audit rows
     * remain queryable even if Java class names are refactored.
     */
    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    /**
     * The id of the row that was mutated, as a CHAR(36) UUID string. Unconstrained by an
     * FK so that referenced rows can be deleted without affecting the audit trail.
     */
    @Column(name = "entity_id", length = 36, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String entityId;

    /**
     * What kind of mutation took place. See {@link AuditAction}. Stored as the enum's
     * {@code name()} (matches the schema's VARCHAR(20) enum-string convention).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private AuditAction action;

    /**
     * The id of the {@code users} row that initiated the mutation, or {@code null} for
     * system actions (e.g., a scheduled job). For impersonation sessions this is the
     * <em>impersonated</em> user, not the admin — see {@link #impersonatedByUserId}.
     */
    @Column(name = "actor_user_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String actorUserId;

    /**
     * Snapshot of the actor's display name at the time of the action. Persisting this
     * makes the audit log readable in support tools without having to join to the (possibly
     * later-renamed-or-deleted) {@code users} row. {@code null} for system actions.
     */
    @Column(name = "actor_name", length = 255)
    private String actorName;

    /**
     * For admin-impersonation sessions only: the id of the {@code COGNILOGISTIC_ADMIN}
     * user who initiated the session. {@code null} for non-impersonation activity. The
     * impersonation session itself is recorded in
     * {@code impersonation_audit_log} (admin module).
     *
     * <p>Indexed via {@code idx_audit_impersonation} for fast "show me everything Admin X
     * did across the platform" queries.
     */
    @Column(name = "impersonated_by_user_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String impersonatedByUserId;

    /**
     * JSON snapshot of the row before the mutation, or {@code null} for {@link AuditAction#CREATE}.
     * Stored in MySQL as a JSON column. Carries only the fields that changed (not the entire row)
     * so audit storage stays bounded. NOTE: PII columns (phone, full name) are NOT redacted at
     * write time — we want auditors to see what changed; redaction happens in the read API
     * for non-privileged callers.
     */
    @Column(name = "old_value", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    /**
     * JSON snapshot of the changed fields after the mutation, or {@code null} for
     * {@link AuditAction#DELETE}.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} pins the JDBC type so Hibernate
     * {@code ddl-auto: validate} accepts the {@code String} field against the
     * {@code JSON} column. {@code columnDefinition} alone is a DDL-generation hint
     * — it doesn't influence validation.
     */
    @Column(name = "new_value", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    /**
     * IP address from which the request originated. IPv4 (max 15 chars) or IPv6 (up to 45 chars)
     * — column is sized 45 to accommodate either. May be {@code null} for system-internal events.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Server timestamp of when this audit row was inserted. Set by
     * {@link com.cognilogistic.platform.audit.AuditService#append} or by the database
     * default ({@code DEFAULT CURRENT_TIMESTAMP}). Never updated.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
