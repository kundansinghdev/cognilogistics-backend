package com.cognilogistic.order.model;

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
 * JPA entity for the {@code customer_contacts} table — one of N contact people
 * associated with a customer (DD-CUST-02 normalisation).
 *
 * <p>Drives notification routing — different events go to different
 * {@link #contactType}s. See {@link ContactType} for the conventions.
 *
 * <p>The application enforces "exactly one row per customer with {@link #isPrimary}=TRUE"
 * on update; the schema doesn't (keeps INSERT simple).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 361–373.
 */
@Entity
@Table(name = "customer_contacts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CustomerContact {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** FK to {@code customers.id}. CASCADE delete from the parent customer. */
    @Column(name = "customer_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String customerId;

    /** PRIMARY / FINANCE / LOGISTICS. Drives notification routing. */
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType = ContactType.PRIMARY;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    /** At least one of {@code phone} / {@code email} should be non-null in practice. */
    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    /**
     * TRUE for the customer's main day-to-day contact. By convention, exactly one
     * row per customer carries {@code is_primary=TRUE}; the service maintains the
     * invariant on update.
     */
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
