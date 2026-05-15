package com.cognilogistic.order.model;

import com.cognilogistic.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity for the order aggregate — the central business object.
 *
 * <p><strong>DD-ORD-02 — two physical tables, one Java entity.</strong>
 * The schema splits orders into:
 * <ul>
 *   <li>{@code orders} — narrow hot table, scanned on every list / dashboard query.</li>
 *   <li>{@code order_details} — heavy fields (cargo, fleet, pricing), 1:1, joined only on detail view.</li>
 * </ul>
 * We use JPA's {@link SecondaryTable} to map a single {@code Order} entity onto both
 * tables — service code stays unchanged while the schema gets the v5.0 split. Each field
 * is annotated with {@code @Column(table=...)} to direct it to the right physical table.
 *
 * <p><strong>Performance note:</strong> with {@code @SecondaryTable}, every {@code findById}
 * issues a JOIN across both tables. For UAT this is fine. A future optimisation is to
 * split into two entities ({@code Order} + {@code OrderDetails}) for read-heavy list
 * queries that don't need the details — that's deferred to keep this PR small.
 *
 * <p><strong>State machine</strong> — see {@link OrderStatus}:
 * <pre>{@code
 *   CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED
 *                                                          ↘ CANCELLED  (any pre-IN_TRANSIT)
 * }</pre>
 * No ASSIGNED state (DD-05). No FLEET_PENDING column (DD-07 — query filter only).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 379–438.
 *
 * <p><strong>v5.0 fields removed:</strong> {@code materialDescription}, {@code vehicleId},
 * {@code cancelledReason} — none in v5.0. Marked {@link Transient} so existing service
 * code keeps compiling, but they don't persist. {@code cancelledReason} folded into
 * {@code order_status_log.notes}; {@code vehicleId} is reachable via the fleet module's
 * {@code fleet_orders} + {@code order_fleet_links} chain.
 */
@Entity
@Table(name = "orders")
@SecondaryTable(
    name = "order_details",
    pkJoinColumns = @PrimaryKeyJoinColumn(name = "order_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    // -------------------------------------------------------------------------
    // Hot-table fields — live on the `orders` table.
    // -------------------------------------------------------------------------

    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /**
     * The user who created the order.
     * Java field {@code createdByUserId}; v5.0 column {@code created_by}.
     */
    @Column(name = "created_by", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String createdByUserId;

    @Column(name = "customer_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String customerId;

    /**
     * Branch office handling this order. NULL at CREATED (set during the auto-acknowledge
     * transition — BR-ORD-05).
     *
     * <p>Java field {@code assignedOfficeId}; v5.0 column {@code office_id}.
     */
    @Column(name = "office_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String assignedOfficeId;

    /** Optional Company Master link (BR-ORD-12). */
    @Column(name = "company_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    /**
     * Convenience flag mirroring {@code delivery_type == EXPRESS}. Application keeps
     * the two in sync on every write.
     *
     * <p>Java field {@code express}; v5.0 column {@code is_express}.
     */
    @Column(name = "is_express", nullable = false)
    private boolean express;

    @Column(name = "internal_notes", length = 1000)
    private String internalNotes;

    // -------------------------------------------------------------------------
    // Hot-table fields added in v5.0 — net new compared to legacy entity.
    // -------------------------------------------------------------------------

    /** Human-readable order number (e.g. {@code "COG-20260507-0001"}). Unique per TP. */
    @Column(name = "order_no", length = 30)
    private String orderNo;

    /** NORMAL or EXPRESS. {@link #express} is the convenience boolean. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 10)
    private DeliveryType deliveryType = DeliveryType.NORMAL;

    @Column(name = "pickup_location", length = 500)
    private String pickupLocation;

    @Column(name = "drop_location", length = 500)
    private String dropLocation;

    @Column(name = "pickup_date")
    private java.time.LocalDate pickupDate;

    @Column(name = "expected_delivery_date")
    private java.time.LocalDate expectedDeliveryDate;

    @Column(name = "goods_type", length = 100)
    private String goodsType;

    /** Customer GSTIN snapshot at order time (denormalised — invoices reference this). */
    @Column(name = "customer_gstin", length = 15)
    private String customerGstin;

    /** Customer name snapshot at order time (denormalised — GR/LR reference this). */
    @Column(name = "customer_name", length = 255)
    private String customerName;

    // -------------------------------------------------------------------------
    // Detail-table fields — live on the `order_details` table via @SecondaryTable.
    // -------------------------------------------------------------------------

    @Column(name = "weight_kg", table = "order_details", precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "volume_cbm", table = "order_details", precision = 10, scale = 3)
    private BigDecimal volumeCbm;

    @Column(name = "requested_vehicle_type", table = "order_details", length = 50)
    private String requestedVehicleType;

    /**
     * Vehicle registration number (UPPERCASE, BR-ORD-09 / BR-ORD-11).
     *
     * <p>Java field {@code vehicleRegistration}; v5.0 column {@code vehicle_number} on
     * {@code order_details}.
     */
    @Column(name = "vehicle_number", table = "order_details", length = 20)
    private String vehicleRegistration;

    @Column(name = "driver_name", table = "order_details", length = 255)
    private String driverName;

    /**
     * Driver contact phone — captured at FLEET_CONFIRMED. Optional. Used by the
     * notification module's WhatsApp / SMS dispatch flows to reach the driver
     * directly during transit.
     *
     * <p>Schema reference: {@code order_details.driver_phone} added by migration
     * {@code V20260508002__order_details_driver_phone.sql}.
     */
    @Column(name = "driver_phone", table = "order_details", length = 15)
    private String driverPhone;

    @Column(name = "driver_dl", table = "order_details", length = 30)
    private String driverDl;

    /**
     * Vehicle body type assigned at FLEET_CONFIRMED.
     *
     * <p>Java field {@code vehicleType}; v5.0 column {@code assigned_vehicle_type} on
     * {@code order_details}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_vehicle_type", table = "order_details", length = 50)
    private VehicleType vehicleType;

    @Column(name = "vahan_status", table = "order_details", length = 20)
    private String vahanStatus;

    @Column(name = "sarathi_status", table = "order_details", length = 20)
    private String sarathiStatus;

    /**
     * Freight cost in INR (whole rupees). Schema is {@code INT NOT NULL DEFAULT 0}.
     *
     * <p>Modeled as nullable {@link Integer} (not {@code int} primitive) so service
     * code can distinguish "not set" from "explicitly zero" before defaulting at
     * persist time. (Earlier draft used {@code BigDecimal(precision=14,scale=2)}
     * which mismatched the schema's INT type and failed Hibernate
     * {@code ddl-auto: validate} at boot — fixed 2026-05-08.)
     */
    @Column(name = "price_inr", table = "order_details")
    private Integer priceInr;

    // -------------------------------------------------------------------------
    // v5.0-removed fields — kept @Transient so legacy service code compiles.
    // -------------------------------------------------------------------------

    /**
     * Material description. Not in v5.0 (closest equivalent is {@link #goodsType}).
     * Marked {@link Transient}: legacy service code that calls
     * {@code order.setMaterialDescription(...)} keeps compiling but the value isn't
     * persisted. New code should use {@link #goodsType}.
     */
    @Transient
    private String materialDescription;

    /**
     * Vehicle id. Not in v5.0 — vehicles are reachable via the fleet module's
     * {@code fleet_orders} + {@code order_fleet_links} chain.
     */
    @Transient
    private String vehicleId;

    /**
     * Cancellation reason. Not in v5.0 — the reason text now lives in the
     * {@code order_status_log.notes} row written when the cancel transition fires.
     * Service code that reads/writes this field still compiles but doesn't persist.
     */
    @Transient
    private String cancelledReason;

    /**
     * Generates a UUID for {@link #id} if not already set. Convenience helper.
     *
     * <p>Also wired into JPA's {@link PrePersist} lifecycle below so every persist
     * path auto-assigns an id — callers don't have to remember. We keep the
     * public method for tests / fixtures that build {@code Order} instances and
     * want a deterministic id before save.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    /**
     * JPA pre-persist callback: assigns a UUID id if the caller forgot. This is
     * the belt to {@link #ensureId()}'s suspenders — without it Hibernate throws
     * {@code IdentifierGenerationException: Identifier of entity '...Order'
     * must be manually assigned before calling 'persist()'} the moment any new
     * code path saves an {@code Order} without setting an id first
     * (regression seen 2026-05-12 in {@code OrderService.create}).
     */
    @PrePersist
    void prePersistEnsureId() {
        ensureId();
    }
}
