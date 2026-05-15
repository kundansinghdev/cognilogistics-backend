package com.cognilogistic.tender.model;

import com.cognilogistic.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code tenders} table — a TP's request for transport that
 * Logistics Partners can bid on.
 *
 * <p><strong>Lifecycle</strong> (4-state machine, {@link TenderStatus}):
 * <pre>{@code
 *   DRAFT → IN_PROGRESS → COMPLETED          (TP awarded a bid)
 *                       ↘ CANCELLED          (TP cancelled the tender)
 * }</pre>
 * No skipping; once {@code COMPLETED} or {@code CANCELLED} the tender is terminal.
 *
 * <p><strong>Lifecycle hooks:</strong>
 * <ul>
 *   <li>Create → DRAFT. Editable, not yet broadcast to LPs.</li>
 *   <li>Publish → IN_PROGRESS. Broadcast happens; the BR-PLN-03 BASIC-cap check fires here.</li>
 *   <li>Award → COMPLETED. Sets {@link #awardedTo} to the winning {@code partner_tp_profiles.id}.
 *       All other bids automatically REJECTED.</li>
 *   <li>Cancel → CANCELLED. PENDING bids transition to REJECTED in the same transaction.</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 533–557.
 */
@Entity
@Table(name = "tenders")
@Getter
@Setter
@NoArgsConstructor
public class Tender extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Tenant scope. Stamped server-side from JWT (BR-MT-04). */
    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /** Human-readable tender number, e.g. {@code "TND-20260507-0001"}. Unique per TP. */
    @Column(name = "tender_number", length = 30)
    private String tenderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenderStatus status = TenderStatus.DRAFT;

    /** Optional friendly title for the tender (UI / notification subject). */
    @Column(name = "title", length = 255)
    private String title;

    /** Free-text origin (e.g. "Faridabad"). v5.0 doesn't normalise to lat/lng. */
    @Column(name = "origin", length = 255)
    private String origin;

    /** Free-text destination. */
    @Column(name = "destination", length = 255)
    private String destination;

    /** Vehicle type the TP is requesting (e.g. "32ft container"). Free-text. */
    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "pickup_date")
    private LocalDate pickupDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /**
     * Reference price the TP is willing to pay, in INR (whole rupees). Visible
     * to bidders as guidance. Default 0 / null = "open / not specified".
     */
    @Column(name = "ref_price_inr")
    private Integer refPriceInr;

    /** Free-text instructions / context for bidders. */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Goods category — front-end Tender DTO carries this so partners can filter
     * tenders by what's being shipped.
     *
     * <p>Schema reference: {@code tenders.goods_type} added by migration
     * {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
     */
    @Column(name = "goods_type", length = 100)
    private String goodsType;

    /**
     * JSON array of channels this tender was broadcast through. Subset of
     * {@code ["app", "whatsapp"]}. Appended on each broadcast write — allows the
     * UI to render badges showing how the tender reached its partners.
     *
     * <p>Stored as JSON text rather than a junction table so the read path stays
     * single-row. The service layer coerces between the JSON string and a
     * {@code List<String>} when serving / writing it.
     *
     * <p>Schema reference: {@code tenders.sent_via} added by migration
     * {@code V20260508003}.
     */
    /**
     * {@code @JdbcTypeCode(SqlTypes.JSON)} pins the JDBC type to JSON so Hibernate
     * {@code ddl-auto: validate} accepts the {@code String} field against the
     * {@code JSON} column. Without this, validate fails at boot because String
     * defaults to VARCHAR. The {@code columnDefinition} hint alone isn't enough —
     * it influences DDL generation, not validation.
     */
    @Column(name = "sent_via", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String sentViaJson;

    /**
     * Cached count of distinct partners reached by all broadcasts on this tender.
     * Recomputed on each broadcast write.
     *
     * <p>Schema reference: {@code tenders.broadcast_partner_count} added by migration
     * {@code V20260508003}; default 0.
     */
    @Column(name = "broadcast_partner_count", nullable = false)
    private int broadcastPartnerCount;

    /**
     * The winning bid (FK to {@code bids.id}), set when the tender is awarded.
     * {@link #awardedTo} is the partner profile, derivable from this row but
     * cached separately for query convenience.
     *
     * <p>Schema reference: {@code tenders.awarded_bid_id} added by migration
     * {@code V20260508003}; FK with {@code ON DELETE SET NULL}.
     */
    @Column(name = "awarded_bid_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String awardedBidId;

    /** Audit: which TP user created the tender. */
    @Column(name = "created_by", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String createdBy;

    /**
     * Set when status moves to COMPLETED — the {@code partner_tp_profiles.id} of
     * the winning Logistics Partner. NULL until award.
     */
    @Column(name = "awarded_to", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String awardedTo;

    /**
     * Legacy field — was on the wrong entity (tenders don't have a single weight,
     * they aggregate orders that each have their own). Marked {@link Transient}
     * so existing service code that reads/writes it keeps compiling but the
     * value isn't persisted. Real cargo weight comes from the linked PTL orders.
     */
    @Transient
    private Integer weightKg;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
