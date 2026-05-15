package com.cognilogistic.tender.model;

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
 * JPA entity for the {@code tender_order_refs} join table — many-to-many link
 * from tenders to PTL orders (BR-ORD-15).
 *
 * <p>One PTL tender consolidates N orders that share a route. An order can be
 * linked to at most one tender at a time (enforced by application logic — no DB
 * UNIQUE on {@code order_id} alone, so historical re-links remain visible).
 *
 * <p>The DB has UNIQUE on {@code (tender_id, order_id)} so the same pair can't
 * appear twice on the same tender — application unlinks the old row before
 * inserting if re-linking is desired.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 586–597.
 *
 * <p><strong>Time-type change:</strong> the legacy entity used {@code LocalDateTime};
 * v5.0 uses {@code Instant} for consistency with every other audit timestamp in
 * the system. The schema column is {@code DATETIME}; both Java types map to it.
 */
@Entity
@Table(name = "tender_order_refs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TenderOrderRef {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    @Column(name = "tender_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tenderId;

    /** PTL order being consolidated. Status must be {@code >= ACKNOWLEDGED} (BR-ORD-15). */
    @Column(name = "order_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String orderId;

    /** Server timestamp of the link. Set by JPA auditing on insert. */
    @CreatedDate
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    /**
     * Convenience constructor that generates a UUID and sets the (tender, order) pair.
     * The {@code linkedAt} timestamp is populated by JPA auditing on insert.
     *
     * @param tenderId the tender this order is being linked to
     * @param orderId  the PTL order being consolidated into the tender
     */
    public TenderOrderRef(String tenderId, String orderId) {
        ensureId();
        this.tenderId = tenderId;
        this.orderId = orderId;
    }

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
