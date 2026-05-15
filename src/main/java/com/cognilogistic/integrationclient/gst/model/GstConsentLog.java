package com.cognilogistic.integrationclient.gst.model;

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
 * JPA entity for the {@code gst_consent_log} table — pre-call consent records
 * for GST GSTIN lookups (DPDP requirement).
 *
 * <p>Same shape as Vahan / Sarathi consent logs but keyed on GSTIN. Notably has
 * no {@code order_id} column — GSTIN lookups happen at customer-creation /
 * Company-Master time, not within an order's flow.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 626–634.
 */
@Entity
@Table(name = "gst_consent_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GstConsentLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /**
     * Tenancy scope. {@code NOT NULL} on the schema — every GSTIN lookup is
     * triggered by a TP user inside a TP context. Earlier code omitted this
     * column entirely; that breaks Hibernate's ddl-auto validation at boot
     * because the schema has it as a NOT NULL FK.
     */
    @Column(name = "tp_account_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tpAccountId;

    /** The user who clicked "I consent" on the front-end. */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /** The 15-character GSTIN the consent applies to. */
    @Column(name = "gstin", nullable = false, length = 15)
    private String gstin;

    /**
     * Server timestamp at consent. v5.0 column is {@code consented_at}; the Java
     * field stays {@code consentAt} for service-code readability. (Earlier draft
     * mapped to {@code consent_at} which doesn't exist on the schema.)
     */
    @CreatedDate
    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentAt;

    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
