package com.cognilogistic.order.model;

/**
 * Freight classification for an order.
 *
 * <p>The order type drives multiple downstream behaviours:
 * <ul>
 *   <li>{@code FTL} — requires a vehicle registration (BR-09) and Vahan consent (BR-10)
 *       at fleet confirmation; can form a Connected Lot when two or more FTL orders share
 *       the same vehicle on the same day</li>
 *   <li>{@code PTL} — does not require vehicle details at fleet confirmation;
 *       PTL orders can be grouped into a Tender for fleet bidding</li>
 * </ul>
 */
public enum OrderType {
    /** Full Truck Load — the customer books an entire vehicle. */
    FTL,
    /** Part Truck Load — the customer shares vehicle capacity with other orders. */
    PTL
}
