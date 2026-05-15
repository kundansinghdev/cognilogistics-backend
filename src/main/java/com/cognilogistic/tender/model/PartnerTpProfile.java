package com.cognilogistic.tender.model;

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
 * JPA entity for the {@code partner_tp_profiles} table — Logistics Partner
 * (Partner TP / 3PL / LSP) profile.
 *
 * <p><strong>R4 minimum.</strong> This entity is intentionally narrow for now —
 * just the fields needed to surface a partner's display name in the
 * {@code TenderBid.partnerName} response field. The full profile (service zone,
 * vehicles, languages, address, GSTIN, etc.) lands in PR R5 along with the rest
 * of the partner-network surface.
 *
 * <p>Lives under the {@code tender} module rather than {@code user} because the
 * tender module is the only consumer in R4. PR R5 may move this to {@code user}
 * if the partner-network endpoints prefer that home.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 172–191;
 * created by migration {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
 */
@Entity
@Table(name = "partner_tp_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PartnerTpProfile extends BaseEntity {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** FK to {@code users.id}. UNIQUE — one profile per user. */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /** Display name shown on tender lists, partner pickers, and TenderBid responses. */
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /** Soft-active flag. Inactive partners don't appear in broadcast targeting. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
