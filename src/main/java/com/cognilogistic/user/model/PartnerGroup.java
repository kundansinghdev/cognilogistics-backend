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
 * JPA entity for the {@code partner_groups} table — a TP's named grouping of
 * Logistics Partners for tender-broadcast targeting.
 *
 * <p><strong>Tenancy:</strong> a partner group is private to the TP that created
 * it. Cross-tenant access is rejected at the service layer; the DB has UNIQUE
 * {@code (tp_account_id, name)} so two TPs can both have a group called
 * "North India Express" without collision.
 *
 * <p><strong>Soft-delete:</strong> {@link #isActive} controls whether the group
 * appears in the broadcast-target picker. Inactive groups stay in the DB so
 * historical broadcasts still resolve their targets.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 621–637;
 * created by migration {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
 */
@Entity
@Table(name = "partner_groups")
@Getter
@Setter
@NoArgsConstructor
public class PartnerGroup extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Tenancy. Group is private to this TP. */
    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /** Display name. UNIQUE within the TP. */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Optional description shown in the broadcast picker tooltip. */
    @Column(name = "description", length = 500)
    private String description;

    /** Soft-delete flag. False = hidden from broadcast targeting. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /** Audit: which TP_ADMIN created this group. */
    @Column(name = "created_by", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String createdBy;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
