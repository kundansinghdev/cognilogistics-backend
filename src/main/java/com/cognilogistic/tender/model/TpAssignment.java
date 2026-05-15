package com.cognilogistic.tender.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * JPA entity for the {@code tp_assignments} table — recorded when the TP awards a
 * tender and the platform notifies the winning Logistics Partner.
 *
 * <p>This row is the LP's "you've been assigned to this tender" handshake. The actual
 * vehicle/driver detail (which truck, which driver, etc.) is the LP's commitment back
 * to the TP.
 *
 * <p><strong>⚠️ SCHEMA GAP:</strong> v5.0 schema doesn't include the obvious vehicle /
 * driver columns ({@code vehicle_number}, {@code driver_name}, {@code driver_phone},
 * {@code dl_number}). Tracked in {@code tender.md §10.2} as an open question. For now,
 * the LP's vehicle/driver detail is conveyed via {@link Bid#getNotes()} as a JSON
 * string. Post-UAT we'll add proper columns and migrate.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 574–584.
 */
@Entity
@Table(name = "tp_assignments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TpAssignment {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** The tender this assignment pertains to. CASCADE — deleting the tender wipes assignments. */
    @Column(name = "tender_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tenderId;

    /** The winning Logistics Partner. References {@code partner_tp_profiles.id}. */
    @Column(name = "partner_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String partnerId;

    /**
     * How the assignment notification was delivered: {@code "WHATSAPP"} / {@code "SMS"} /
     * {@code "IN_APP"}. Free-text in v5.0; ops query this to trace which channel reached
     * the LP.
     */
    @Column(name = "sent_via", nullable = false, length = 20)
    private String sentVia;

    @CreatedDate
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
