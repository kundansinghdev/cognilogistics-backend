package com.cognilogistic.order.model;

import com.cognilogistic.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code customers} table — identity + activation state of a
 * customer (shipper / sender).
 *
 * <p>Two states (BR-ORD-04):
 * <ul>
 *   <li><strong>Real customer</strong> — completed portal activation (OTP+PIN);
 *       {@link #shadow}{@code = false}.</li>
 *   <li><strong>Shadow customer</strong> — placeholder created when a TP user enters an
 *       order for a phone the system doesn't yet know, or without a phone; {@link #shadow}{@code = true}.
 *       No portal access until the TP explicitly grants it (see {@code portalAccessEnabled}
 *       below — not in v5.0 schema, see schema-gap note).</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 326–343.
 *
 * <p><strong>Java field naming:</strong> the Java field names ({@code whatsappPhone},
 * {@code name}, {@code createdByTp}) are kept from the legacy code so existing services /
 * controllers / DTOs don't need a rename cascade. The {@code @Column(name=...)} annotations
 * map them to the v5.0 column names ({@code phone}, {@code legal_name}, {@code created_by_tp_id}).
 * A future cleanup PR can rename the Java fields to match v5.0; that's deferred to keep this
 * PR's blast radius manageable.
 *
 * <p><strong>Schema gap — portal access columns:</strong> the {@code portalAccessEnabled},
 * {@code portalAccessGrantedAt}, {@code portalAccessGrantedBy} fields are NOT in the v5.0
 * schema. The customer-portal grant workflow per the docs uses these flags, but v5.0
 * dropped them. Marking them {@link Transient} so the Java code keeps compiling while we
 * confirm with the user whether to (a) re-add the columns, or (b) infer "portal enabled"
 * from {@link #shadow}{@code = false} (i.e. shadow flips to non-shadow on portal grant).
 * The portal flow is in scope, so this needs a decision before the customer-portal
 * implementation lands.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * Customer phone — globally unique among non-null values. May be {@code NULL} for shadow
     * customers created when an order is placed without a WhatsApp number (DD-CUST-02).
     *
     * <p>Java field name kept as {@code whatsappPhone} for legacy service compatibility;
     * v5.0 column is {@code phone}.
     */
    @Column(name = "phone", nullable = true, length = 15, unique = true)
    private String whatsappPhone;

    /**
     * Legal / registered company name. Used on invoices, GR, LR documents.
     *
     * <p>Java field {@code name}; v5.0 column {@code legal_name}.
     */
    @Column(name = "legal_name", length = 255)
    private String name;

    /**
     * TRUE for placeholder customers created during order entry (BR-ORD-04). Flipped to
     * FALSE when the customer activates portal access via OTP + PIN.
     */
    @Column(name = "is_shadow", nullable = false)
    private boolean shadow;

    /**
     * The TP account that originally created this customer record. Tenancy of the row.
     *
     * <p>Java field {@code createdByTp}; v5.0 column {@code created_by_tp_id}.
     */
    @Column(name = "created_by_tp_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String createdByTp;

    // -------------------------------------------------------------------------
    // SCHEMA GAP: portal access columns
    // -------------------------------------------------------------------------
    // These three fields existed in legacy migrations and are referenced by the
    // customer-portal grant flow (docs say "TP_ADMIN grants portal access via this
    // flag"). v5.0 schema doesn't have them. Marked @Transient so the Java code
    // compiles; runtime behaviour will be incorrect until the user confirms whether
    // to re-add the columns or infer portal access from `is_shadow=false`.
    // Tracked as a P1 follow-up: "Resolve customer portal-access schema gap."

    @Transient
    private boolean portalAccessEnabled;

    @Transient
    private Instant portalAccessGrantedAt;

    @Transient
    private String portalAccessGrantedBy;

    /**
     * Generates a UUID for {@link #id} if not already set. Convenience helper.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    /**
     * Factory: constructs a shadow customer during order placement (BR-ORD-04).
     * Sets {@link #shadow}{@code = true}; the TP must explicitly enable portal access later.
     *
     * @param whatsappPhone the customer's phone number, or {@code null} for a phone-less shadow row
     * @param createdByTp   the TP account UUID that triggered the auto-creation
     * @return unsaved Customer with a generated id, ready to {@code save()}
     */
    public static Customer newShadow(String whatsappPhone, String createdByTp) {
        Customer c = new Customer();
        c.ensureId();
        c.whatsappPhone = whatsappPhone;
        c.shadow = true;
        c.createdByTp = createdByTp;
        return c;
    }
}
