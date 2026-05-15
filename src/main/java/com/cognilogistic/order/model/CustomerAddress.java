package com.cognilogistic.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code customer_addresses} table — one of N addresses attached to
 * a customer (DD-CUST-02 normalisation).
 *
 * <p>A customer may have multiple billing or shipping addresses (e.g. multiple warehouses).
 * The {@link #isDefault} flag picks the preferred address within each
 * {@link #addressType} for auto-fill on order creation. The application enforces the
 * "exactly one default per (customer, address_type)" invariant on update — the schema
 * doesn't (deliberately, to keep INSERT atomicity simple).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 345–358.
 */
@Entity
@Table(name = "customer_addresses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** FK to {@code customers.id}. CASCADE delete from the parent customer. */
    @Column(name = "customer_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String customerId;

    /** BILLING / SHIPPING / BOTH. Drives invoice routing and order auto-fill. */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType = AddressType.BILLING;

    @Column(name = "address_line_1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /**
     * Indian PIN code. VARCHAR(10) (not 6) so third-party APIs that deliver formatted
     * codes (e.g. {@code "121-001"}) don't trip on length.
     */
    @Column(name = "pincode", length = 10)
    private String pincode;

    /**
     * TRUE for the customer's preferred address of this {@link #addressType}. The
     * service maintains the "exactly one default per type" invariant when updating.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Generates a UUID for {@link #id} if not already set. Convenience helper so service
     * code doesn't have to call {@link UUID#randomUUID()} explicitly before save.
     */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
