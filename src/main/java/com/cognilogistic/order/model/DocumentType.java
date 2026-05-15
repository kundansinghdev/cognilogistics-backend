package com.cognilogistic.order.model;

/**
 * Type of document attached to an order. Stored as the enum's {@code name()} string in
 * the {@code order_documents.doc_type} column (VARCHAR(20)).
 *
 * <p>Drives the UI presentation (different icons / groupings per type) and enables
 * type-filtered queries like "show me this order's GR PDF."
 */
public enum DocumentType {

    /**
     * Goods Receipt — generated automatically when the order moves into FLEET_CONFIRMED
     * (truck has been assigned). Per-order PDF rendered server-side.
     */
    GR,

    /**
     * Lorry Receipt — covers a connected lot (BR-ORD-14: same vehicle + pickup_date forms
     * a lot). One LR per lot; each order in the lot gets a row pointing to the same blob.
     * Generation logic lives in the fleet module's {@code lorry_receipts} table; this
     * row is the per-order pointer for cross-module retrieval.
     */
    LR,

    /** Billing PDF generated when an order is invoiced. Post-UAT for full lifecycle. */
    INVOICE,

    /** Anything else the TP wants to attach (delivery proof photos, etc.). */
    CUSTOM
}
