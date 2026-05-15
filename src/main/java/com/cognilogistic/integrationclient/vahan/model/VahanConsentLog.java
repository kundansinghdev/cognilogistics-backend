package com.cognilogistic.integrationclient.vahan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
 * JPA entity for the {@code vahan_consent_log} table — pre-call consent records
 * for Vahan vehicle-registry lookups (DPDP requirement, BR-ORD-10).
 *
 * <p>Each row captures one consent event. The
 * {@link com.cognilogistic.integrationclient.vahan.VahanService} verifies that a
 * recent row exists for the (orderId, vehicleRegistration) pair before allowing
 * a Vahan API call — even when running in mock mode (the consent log is what we'd
 * show a DPDP auditor regardless of whether the call was real or mocked).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 603–613.
 *
 * <p><strong>v5.0 simplification — fields removed:</strong> the legacy entity had
 * {@code consent_text}, {@code consent_given}, {@code mock_mode}. v5.0 dropped them:
 * <ul>
 *   <li>{@code consent_text} — boilerplate consent wording belongs in front-end code,
 *       not in N copies in DB.</li>
 *   <li>{@code consent_given} — implied. The presence of a row IS consent. A
 *       declined click simply doesn't INSERT.</li>
 *   <li>{@code mock_mode} — moved to {@code external_api_audit_log.mock_used}, which
 *       is the right place for it (consent is independent of how the call was served).</li>
 * </ul>
 * Marked {@link Transient} so existing service code keeps compiling but the values
 * don't persist. New code shouldn't write or read them.
 *
 * <p><strong>Column rename:</strong> {@code vehicle_registration} → {@code vehicle_reg}
 * (v5.0 column name). Java field name kept as {@code vehicleRegistration} so service
 * code doesn't need a rename.
 */
@Entity
@Table(name = "vahan_consent_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class VahanConsentLog {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * Order context for the consent. {@code NOT NULL} on the v5.0 schema —
     * earlier code marked this nullable but the column is mandatory at the DB
     * level. Hibernate {@code ddl-auto: validate} would have failed at boot.
     */
    @Column(name = "order_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String orderId;

    /** The user who clicked "I consent" on the front-end. */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Vehicle registration the consent applies to. UPPERCASE.
     *
     * <p>v5.0 schema column is {@code reg_number}. The Java field name stays
     * {@code vehicleRegistration} so service code doesn't need a rename. (Earlier
     * draft mapped to {@code vehicle_reg} which doesn't exist on the schema.)
     */
    @Column(name = "reg_number", nullable = false, length = 20)
    private String vehicleRegistration;

    /**
     * Server timestamp of the consent. Set automatically by JPA auditing.
     *
     * <p>v5.0 schema column is {@code consented_at}. Java field stays
     * {@code createdAt} for service-code readability. (Earlier draft mapped to
     * {@code consent_at} which doesn't exist on the schema.)
     */
    @CreatedDate
    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant createdAt;

    // -------------------------------------------------------------------------
    // v5.0-removed fields — kept @Transient so existing VahanService code compiles.
    // See class header for rationale on each.
    // -------------------------------------------------------------------------

    @Transient
    private String consentText;

    @Transient
    private boolean consentGiven;

    @Transient
    private boolean mockMode;

    /** Generates a UUID for {@link #id} if not already set. */
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
