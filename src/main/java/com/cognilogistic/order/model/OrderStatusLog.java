package com.cognilogistic.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * JPA entity for the {@code order_status_log} table — immutable audit trail of every
 * status transition an order undergoes, including the initial CREATED row (BR-ORD-06).
 *
 * <p>Rows are append-only; the application never updates or deletes them. The
 * {@code triggered_at} timestamp is set automatically by JPA auditing
 * ({@link CreatedDate}) at insert time.
 *
 * <p>The CREATED row has {@code from_status = NULL} because there is no preceding state.
 * Subsequent rows always have a non-null {@code from_status}.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 441–451.
 *
 * <p><strong>Java field naming:</strong>
 * <ul>
 *   <li>Java {@code triggeredByUserId}, v5.0 column {@code triggered_by} (renamed).</li>
 *   <li>Java {@code note}, v5.0 column {@code notes} (plural).</li>
 *   <li>{@code triggered_by} is now NULLABLE in v5.0 (was NOT NULL legacy) so
 *       system-driven transitions can record themselves with a null actor.</li>
 * </ul>
 */
@Entity
@Table(name = "order_status_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusLog {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** FK to {@code orders.id}. CASCADE delete from the parent order. */
    @Column(name = "order_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String orderId;

    /** Status before the transition. NULL only for the initial CREATED row. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    /** Status after the transition. Always set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    /**
     * The user who triggered the transition.
     *
     * <p>Java field {@code triggeredByUserId}; v5.0 column {@code triggered_by}.
     * NULLABLE — system-driven transitions (e.g. scheduled-job auto-cancel post-UAT)
     * record themselves with a null actor.
     */
    @Column(name = "triggered_by", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String triggeredByUserId;

    /**
     * Server timestamp of the transition. Set by JPA auditing on insert.
     */
    @CreatedDate
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt;

    /**
     * Free-text notes — used especially for cancel reasons (BR-ORD-02).
     *
     * <p>Java field {@code note}; v5.0 column {@code notes}.
     */
    @Column(name = "notes", length = 500)
    private String note;

    /**
     * Generates a UUID for {@link #id} if not already set. Convenience helper so
     * service code doesn't have to call {@link UUID#randomUUID()} explicitly.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    /**
     * Ensures {@link #id} is set before Hibernate {@code persist()} — matches the
     * pattern on {@link Order}; without this, {@code OrderService#create} fails
     * right after the order row inserts when {@code writeLog} saves the first
     * CREATED audit row.
     */
    @PrePersist
    void prePersistEnsureId() {
        ensureId();
    }
}
