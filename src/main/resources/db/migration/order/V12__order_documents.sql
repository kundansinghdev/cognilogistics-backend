-- =============================================================================
-- Migration:  V12__order_documents.sql   (NEW BASELINE — schema.sql v5.0 alignment)
-- Module:     order
-- Date:       2026-05-07
-- Author:     CogniLogistic Platform Team
--
-- WHAT THIS MIGRATION DOES:
--   Creates the `order_documents` table — pointers to PDF / image documents
--   generated for or attached to an order (Goods Receipt, Lorry Receipt,
--   invoices, custom uploads).
--
-- DOCUMENT TYPES:
--   - GR  (Goods Receipt) — generated when an order is placed and shipment is
--                            confirmed. PDF rendered server-side.
--   - LR  (Lorry Receipt) — covers a connected lot (BR-ORD-14: same vehicle +
--                            pickup_date forms a lot). One LR per lot, referenced
--                            by every order in the lot. Note: LR generation logic
--                            lives in the fleet module's `lorry_receipts` table;
--                            this row holds a per-order pointer to the same blob.
--   - INVOICE — billing PDF.
--   - CUSTOM  — anything else the TP wants to attach.
--
-- BLOB STORAGE:
--   The PDFs / images themselves live in Azure Blob storage. This table only
--   stores the URL and metadata. file_url is a pre-signed SAS URL valid for the
--   document's lifetime; renewals happen on-demand when the URL expires.
-- =============================================================================

CREATE TABLE order_documents (

    id              CHAR(36)        NOT NULL,
    order_id        CHAR(36)        NOT NULL,

    -- GR / LR / INVOICE / CUSTOM. Drives UI presentation (different icons /
    -- groupings for each type).
    doc_type        VARCHAR(20)     NOT NULL,

    -- Pre-signed Azure Blob URL. Up to 1000 chars because SAS query strings
    -- can be long (signature + permissions + expiry + headers).
    file_url        VARCHAR(1000)   NOT NULL,

    -- The user who uploaded the document. NULL for system-generated GR / LR
    -- (those have no human "uploader" — they're rendered server-side).
    uploaded_by     CHAR(36)        NULL,

    uploaded_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Hot path: "show me all docs for this order" — order detail page.
    INDEX idx_od_order (order_id),

    -- RESTRICT (not CASCADE): once a doc has been issued (especially a GR or
    -- INVOICE) it shouldn't be silently lost. If an order is hard-deleted with
    -- docs attached, the FK will block — forcing the operator to consciously
    -- archive or detach the documents first.
    CONSTRAINT fk_odoc_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-order document pointers (GR / LR / invoice / custom). v5.0 schema.';
