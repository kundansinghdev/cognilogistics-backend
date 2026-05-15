package com.cognilogistic.tender.model;

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
 * JPA entity for the {@code bids} table — a Logistics Partner's offer on a tender.
 *
 * <p>Sealed-envelope: each LP sees only their own bid. The owning TP sees all bids on
 * their tender and picks one. When the TP awards a bid:
 * <ul>
 *   <li>The chosen bid → {@link BidStatus#ACCEPTED}</li>
 *   <li>All sibling bids on the same tender → {@link BidStatus#REJECTED}</li>
 *   <li>The parent {@link Tender} → {@link TenderStatus#COMPLETED}</li>
 *   <li>{@link Tender#awardedTo} is set to {@link #partnerId}</li>
 * </ul>
 *
 * <p><strong>One bid per (tender, partner) — DB UNIQUE.</strong> If an LP withdraws and
 * wants to re-bid, the application updates this same row (status / amount / notes back
 * to PENDING) rather than INSERT-ing a second row.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 559–572.
 */
@Entity
@Table(name = "bids")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Bid {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** The tender this bid is on. CASCADE — deleting the tender wipes its bids. */
    @Column(name = "tender_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tenderId;

    /**
     * The Logistics Partner placing the bid. References {@code partner_tp_profiles.id}
     * — FK constraint added when that table comes online (post-UAT).
     */
    @Column(name = "partner_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String partnerId;

    /** Offered price in INR (whole rupees). */
    @Column(name = "amount_inr", nullable = false)
    private Integer amountInr;

    /** Estimated days from pickup to delivery. NULL if the LP doesn't commit. */
    @Column(name = "eta_days")
    private Integer etaDays;

    /** Free-text bid context (e.g. "Available May 10 onwards, returning empty"). */
    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BidStatus status = BidStatus.PENDING;

    @CreatedDate
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
