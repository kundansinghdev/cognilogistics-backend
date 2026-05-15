package com.cognilogistic.tender.model;

/**
 * Lifecycle of a Logistics Partner's bid on a tender. Stored as the enum's
 * {@code name()} string in the {@code bids.status} column (VARCHAR(20)).
 *
 * <p><strong>Lifecycle:</strong>
 * <pre>{@code
 *   PENDING ─► ACCEPTED       (TP awarded this bid)
 *           ─► REJECTED       (TP rejected this specific bid, OR awarded a sibling)
 *           ─► WITHDRAWN      (LP voluntarily withdrew before TP decision)
 * }</pre>
 *
 * <p>Once the bid leaves PENDING it's terminal for the original submission. If an LP
 * withdraws and wants to re-bid, the application updates the row's status / amount /
 * notes back to PENDING (the DB UNIQUE on {@code (tender_id, partner_id)} prevents a
 * second row).
 */
public enum BidStatus {

    /** Bid is live; the TP hasn't decided yet. */
    PENDING,

    /** TP awarded this bid — the Logistics Partner has won the tender. */
    ACCEPTED,

    /** Bid was declined (either explicitly by the TP, or implicitly when a sibling won). */
    REJECTED,

    /** LP voluntarily withdrew before the TP decided. */
    WITHDRAWN
}
