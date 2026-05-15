package com.cognilogistic.user.model;

import com.cognilogistic.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * JPA entity for the {@code offices} table — a branch location of a TP account.
 *
 * <p>Every order is assigned to exactly one office ({@code orders.office_id}). Branch
 * staff ({@code TP_TRANSPORT_MANAGER} role) are scoped to specific offices via
 * {@link UserOfficeAssignment} and can only operate on orders for their assigned offices.
 * TP_ADMIN bypasses the membership check.
 *
 * <p><strong>{@link #isActive}</strong> is the soft-delete flag (DD-08, BR-OFF-04). Once an office
 * has orders in history it must never be hard-deleted; flip {@code is_active=false} instead. Inactive
 * offices are excluded from order-assignment dropdowns by
 * {@link com.cognilogistic.user.repository.OfficeRepository#findByTpAccountIdAndIsActive}.
 *
 * <p><strong>{@link #code}</strong> is a short mnemonic (e.g. {@code "FB1"}) unique within
 * the parent TP account (BR-OFF-02). Normalised to uppercase by the service before persist
 * (BR-OFF-07).
 *
 * <p><strong>v5.0 change — {@code is_primary} removed:</strong> earlier drafts had a
 * "designated primary office" concept. v5.0 dropped it; offices are equal first-class citizens
 * and any UI that wants to highlight a head office derives it from creation order or other
 * application logic, not a schema column.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 194–213.
 */
@Entity
@Table(name = "offices")
@Getter
@Setter
@NoArgsConstructor
public class Office extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side in {@code OfficeService.create} via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Owning TP account. Offices are never shared across TP accounts (BR-MT-01). */
    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /** Display name of the branch office, e.g. {@code "Faridabad Hub 1"}. Required (BR-OFF-01). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Short mnemonic code unique within the TP account (BR-OFF-02), e.g. {@code "FB1"}.
     * Auto-uppercased by {@code OfficeService} before INSERT (BR-OFF-07). Max 10 chars.
     */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /**
     * City where the office is located. Required (BR-OFF-01).
     * Stored as VARCHAR(100) so adding a new city doesn't require a migration.
     */
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** State where the office is located. Required (BR-OFF-01). VARCHAR(100). */
    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /**
     * Postal / PIN code. Held as VARCHAR(10) — third-party APIs sometimes deliver PIN codes
     * with separators (e.g. {@code "121-001"}); we want flexibility.
     */
    @Column(name = "pincode", length = 10)
    private String pincode;

    /** Free-text full street address. Used on GR / LR documents and shipping labels. */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * Optional branch-level GSTIN (15 chars). An office GSTIN may differ from the parent
     * TP account's GSTIN — Indian branch-level GST registration is valid.
     * Validated against the standard GSTIN regex by the service if non-null (BR-OFF-03).
     */
    @Column(name = "gstin", length = 15)
    private String gstin;

    /**
     * Soft-delete flag. {@code true} = office is operational and appears in dropdowns.
     * {@code false} = office is deactivated and hidden from new order assignment, but
     * historical data is preserved (DD-08, BR-OFF-04). The deactivation transition is
     * blocked by BR-OFF-06 if any non-DELIVERED / non-CANCELLED orders are still assigned
     * to this office.
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * Generates and assigns a UUID to {@link #id} if not already set. Convenience helper
     * so {@code OfficeService.create} doesn't have to call {@link UUID#randomUUID()} explicitly.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
