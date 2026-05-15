package com.cognilogistic.integrationclient.audit;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code external_api_audit_log} table — generic audit of every
 * outbound call to an external integration (Vahan / Sarathi / GST).
 *
 * <p>Used by ops to answer:
 * <ul>
 *   <li>"How many real Vahan calls did we make this week?" — filter
 *       {@code service='VAHAN' AND mock_used=false}.</li>
 *   <li>"What's the median Sarathi latency right now?" — aggregate
 *       {@link #responseMs}.</li>
 * </ul>
 *
 * <p>Notably <strong>no request/response body capture</strong> and no per-call
 * endpoint / HTTP-status-code text — v5.0 schema kept the audit minimal to
 * avoid storage cost and PII-redaction complexity. The integration's own logs
 * carry the verbose detail; this row is the metadata pointer.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 709–719.
 *
 * <p><strong>Schema drift fixed 2026-05-08:</strong> earlier draft used a richer
 * column set ({@code integration_name} / {@code endpoint} / {@code http_status} /
 * {@code latency_ms} / {@code related_entity_*}) that diverged from canonical
 * schema. Aligned to v5.0 here so Hibernate's {@code ddl-auto: validate} passes
 * at boot.
 */
@Entity
@Table(name = "external_api_audit_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExternalApiAuditLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Which external system was called: {@code "VAHAN"} / {@code "SARATHI"} / {@code "GST"}. */
    @Column(name = "service", nullable = false, length = 20)
    private String service;

    /**
     * The entity reference the call was about — typically an {@code order_id}
     * for VAHAN / SARATHI calls and a {@code tp_account_id} for GST calls.
     * NULLABLE for system-initiated calls (scheduled re-verification, post-UAT).
     */
    @Column(name = "request_ref", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestRef;

    /** HTTP status code from the upstream (or null if the call never reached the wire). */
    @Column(name = "status_code")
    private Integer statusCode;

    /** Round-trip latency in milliseconds. NULL for failures that never returned. */
    @Column(name = "response_ms")
    private Integer responseMs;

    /**
     * TRUE = the mock client served this call (no actual network call made).
     * Powers ops queries that filter "real calls only" (e.g. cost reconciliation).
     */
    @Column(name = "mock_used", nullable = false)
    private boolean mockUsed;

    @CreatedDate
    @Column(name = "called_at", nullable = false, updatable = false)
    private Instant calledAt;

    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
