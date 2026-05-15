package com.cognilogistic.tender.model;

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

/**
 * JPA entity for the {@code tender_assignments} table — vehicle + driver
 * submitted by the winning partner after a tender is awarded.
 *
 * <p><strong>1:1 with {@link Tender}</strong> — the tender id IS the primary
 * key. Locks once written: the FE hides the submit form and the partner-portal
 * service rejects further attempts with {@link com.cognilogistic.platform.api.ErrorCode#INVALID_TRANSITION}.
 *
 * <p>Distinct from the per-order fleet fields ({@code order_details.vehicle_number}
 * / {@code .driver_*}) because one tender can later spawn N PTL orders, each
 * carrying their own per-leg fleet info.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 659–670;
 * created by migration {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
 */
@Entity
@Table(name = "tender_assignments")
@Getter
@Setter
@NoArgsConstructor
public class TenderAssignment {

    /** PK is the tender id — one row per tender. CASCADE on tender delete. */
    @Id
    @Column(name = "tender_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tenderId;

    /** Indian vehicle registration; uppercased server-side before save. */
    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(name = "driver_name", nullable = false, length = 255)
    private String driverName;

    /** Driver licence number; uppercased before save. Optional. */
    @Column(name = "driver_dl", length = 30)
    private String driverDl;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** The PARTNER_TP user who submitted the assignment. */
    @Column(name = "assigned_by_user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String assignedByUserId;
}
