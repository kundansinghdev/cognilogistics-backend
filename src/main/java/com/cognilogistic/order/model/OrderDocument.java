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
 * JPA entity for the {@code order_documents} table — pointers to PDF / image documents
 * generated for or attached to an order.
 *
 * <p>The actual blobs (PDFs / images) live in Azure Blob storage. This row stores only
 * the pre-signed SAS URL plus metadata. URLs renew on demand when they expire — the
 * service layer handles refresh transparently.
 *
 * <p><strong>Document types</strong> — see {@link DocumentType}:
 * <ul>
 *   <li>{@link DocumentType#GR} — Goods Receipt (per order, server-rendered at FLEET_CONFIRMED)</li>
 *   <li>{@link DocumentType#LR} — Lorry Receipt (per connected lot — see fleet module)</li>
 *   <li>{@link DocumentType#INVOICE} — billing PDF</li>
 *   <li>{@link DocumentType#CUSTOM} — anything else (delivery photos, etc.)</li>
 * </ul>
 *
 * <p><strong>FK is RESTRICT, not CASCADE.</strong> Once a document has been issued
 * (especially a GR or INVOICE) it shouldn't be silently lost when an order is hard-deleted.
 * The FK forces a deliberate "archive or detach the documents first" step.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 454–464.
 */
@Entity
@Table(name = "order_documents")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class OrderDocument {

    /** CHAR(36) UUID. Generated server-side via {@link #ensureId()}. */
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String id;

    /** FK to {@code orders.id}. ON DELETE RESTRICT — see class header. */
    @Column(name = "order_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String orderId;

    /** GR / LR / INVOICE / CUSTOM. Drives UI presentation (icons, groupings). */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType docType;

    /**
     * Pre-signed Azure Blob SAS URL. Up to 1000 chars because SAS query strings can be
     * long (signature + permissions + expiry + headers). When the URL expires, the service
     * layer mints a fresh one against the same blob path.
     */
    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /**
     * The user who uploaded the document. NULL for system-generated GR / LR / INVOICE
     * (those are rendered server-side and have no human "uploader").
     */
    @Column(name = "uploaded_by", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String uploadedBy;

    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
