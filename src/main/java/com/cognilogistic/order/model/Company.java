package com.cognilogistic.order.model;

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
 * JPA entity for the {@code companies} table — the per-TP Company Master GSTIN
 * registry (BR-ORD-12).
 *
 * <p><strong>Purpose:</strong> when a TP user enters a 15-character GSTIN at order
 * creation, the front-end does an inline lookup to this table:
 * <pre>{@code
 *   SELECT legal_name, primary_contact_*, address_*
 *     FROM companies
 *    WHERE tp_account_id = ? AND gstin = ?
 * }</pre>
 * If found, the customer name and contact fields auto-populate. If not found, the
 * user is offered an "Add to Company Master" CTA which inserts a row here for next
 * time. Kept as a per-TP curated list, not a global registry.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 294–320.
 *
 * <p><strong>Java field naming:</strong> the legacy entity used {@code name} /
 * {@code addressLine1} / {@code contactName}; v5.0 columns are {@code legal_name} /
 * {@code address_line_1} / {@code primary_contact_name}. Java field names kept where
 * service code already uses them; {@code @Column(name=...)} aliases handle the column
 * rename. Net-new fields ({@code tradeName}, {@code noGst}, {@code notes},
 * {@code isActive}, {@code createdByUserId}) added.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Tenant scope — each TP curates their own company master list (BR-MT-01). */
    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /**
     * Registered legal name. Used for invoice / GR / LR auto-fill.
     *
     * <p>Java field {@code name}; v5.0 column {@code legal_name}.
     */
    @Column(name = "legal_name", nullable = false, length = 255)
    private String name;

    /** Optional brand / trade name for display when different from legal name. */
    @Column(name = "trade_name", length = 255)
    private String tradeName;

    /**
     * 15-character GSTIN. NULL when {@link #noGst} is TRUE. The lookup key for
     * BR-ORD-12 GSTIN auto-fill.
     */
    @Column(name = "gstin", length = 15)
    private String gstin;

    /** TRUE = company is not GST-registered. When TRUE, {@link #gstin} must be NULL. */
    @Column(name = "no_gst", nullable = false)
    private boolean noGst;

    /** Java field {@code addressLine1}; v5.0 column {@code address_line_1} (with underscore). */
    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    /**
     * Primary contact at the company. Single set of fields here (not a list) — Company
     * Master is a quick-lookup cache, not a full CRM. Multi-contact richness lives in
     * {@link CustomerContact} for activated customers.
     *
     * <p>Java field {@code contactName}; v5.0 column {@code primary_contact_name}.
     */
    @Column(name = "primary_contact_name", length = 255)
    private String contactName;

    /** Java field {@code contactPhone}; v5.0 column {@code primary_contact_phone}. */
    @Column(name = "primary_contact_phone", length = 15)
    private String contactPhone;

    /** Java field {@code contactEmail}; v5.0 column {@code primary_contact_email}. */
    @Column(name = "primary_contact_email", length = 255)
    private String contactEmail;

    /** Free-text internal notes (e.g. "Always call on Mondays before 11am"). */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Soft-delete flag. Inactive companies don't appear in the GSTIN lookup dropdown but
     * historical data is preserved.
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /** Audit: which user added this company to the master. */
    @Column(name = "created_by_user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String createdByUserId;

    /**
     * Generates a UUID for {@link #id} if not already set.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
