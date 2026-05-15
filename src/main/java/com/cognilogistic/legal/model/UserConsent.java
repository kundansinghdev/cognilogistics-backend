package com.cognilogistic.legal.model;

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
 * JPA entity for the {@code user_consents} table — append-only audit log of every
 * legal-doc consent event (T&amp;C / Privacy at signup; future re-acceptance flows).
 *
 * <p>The DPDPA / IT Act audit story this entity answers: <em>"prove which user
 * agreed to which version, when, and from where"</em>. Each row carries
 * server-set {@link #acceptedAt}, {@link #ipAddress}, and {@link #userAgent} —
 * NEVER trust client-supplied values for these fields.
 *
 * <p><strong>UNIQUE (user_id, doc_type, doc_version)</strong> at the DB level
 * (uq_user_consent) blocks replays. Service code translates a constraint
 * violation on this index into an idempotent success — re-submitting the same
 * accept twice returns 200, not 500.
 *
 * <p><strong>Schema reference:</strong> {@code V20260508005__user_consents_and_legal_doc_versions.sql}.
 */
@Entity
@Table(name = "user_consents")
@Getter
@Setter
@NoArgsConstructor
public class UserConsent {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * The user who clicked "I accept". CASCADE — deleting a user wipes their
     * consent rows by default. If legal subsequently rules consent rows must
     * outlive user erasure as evidence, change the FK to {@code SET NULL} and
     * make this column nullable in a follow-up migration.
     */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /** TERMS or PRIVACY. */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private DocType docType;

    /**
     * Version that was accepted (e.g. {@code "2026-05-08"}). Snapshot — even
     * after legal republishes a new version, this row keeps the old string so
     * the audit trail for this user stays intact.
     */
    @Column(name = "doc_version", nullable = false, length = 32)
    private String docVersion;

    /**
     * Server clock at consent. Set in service code so unit tests with a fixed
     * Clock are deterministic; DB has {@code DEFAULT CURRENT_TIMESTAMP} as a
     * safety net for raw-SQL inserts.
     */
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    /**
     * Client IP address at consent. Extracted server-side after stripping
     * trusted-proxy hops (see BACKEND_SPEC_TC_CONSENT.md §5.3). NULL if
     * the request had no resolvable IP (rare; e.g. system-internal calls).
     *
     * <p>VARCHAR(45) covers IPv6 worst-case ({@code ::ffff:255.255.255.255}).
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User-Agent header verbatim. {@code TEXT} (vs VARCHAR) because modern
     * Chrome / Edge / mobile UAs are routinely 200+ chars and growing —
     * silently truncating evidence is worse than the storage cost.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)} aligns the Java
     * {@code String} field with the {@code TEXT} column so Hibernate's
     * ddl-auto: validate accepts the mapping.
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String userAgent;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
