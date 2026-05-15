package com.cognilogistic.integrationclient.sarathi.model;

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
 * JPA entity for the {@code sarathi_consent_log} table — pre-call consent records
 * for Sarathi driving-licence-registry lookups (DPDP requirement).
 *
 * <p>Same pattern as {@link com.cognilogistic.integrationclient.vahan.model.VahanConsentLog}
 * but keyed on the DL number rather than vehicle registration. The presence of a row
 * IS consent — a declined click doesn't INSERT.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 615–624.
 */
@Entity
@Table(name = "sarathi_consent_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SarathiConsentLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** Driving licence number the consent applies to. UPPERCASE. */
    @Column(name = "dl_number", nullable = false, length = 20)
    private String dlNumber;

    /** The user who clicked "I consent" on the front-end. */
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /**
     * Order context for the consent. {@code NOT NULL} on the schema
     * ({@code sarathi_consent_log.order_id}). Earlier code marked this nullable to
     * support a hypothetical "driver-onboarding bulk verification" flow that never
     * landed — removed so the entity matches the actual column shape and
     * Hibernate's {@code ddl-auto: validate} passes at boot.
     */
    @Column(name = "order_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String orderId;

    /**
     * Server timestamp at consent. v5.0 schema column is {@code consented_at};
     * the Java field stays {@code consentAt} for service-code readability.
     * (Earlier draft mapped to {@code consent_at} which doesn't exist on the
     * schema — that would have failed Hibernate validation at boot.)
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
