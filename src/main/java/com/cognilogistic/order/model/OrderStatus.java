package com.cognilogistic.order.model;

/**
 * The only valid order statuses in the V3.6 order lifecycle.
 *
 * <p>Forward path: {@code CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED}.
 * {@code CANCELLED} is reachable from any pre-transit state.
 *
 * <p>Note: {@code FLEET_PENDING} is intentionally NOT a status — it is a computed query filter
 * (BR-07): {@code WHERE status = 'ACKNOWLEDGED' AND (order_type = 'PTL' OR vehicle_id IS NOT NULL)}.
 *
 * <p>Note: {@code ASSIGNED} was removed in V3.6. Setting {@code office_id} is now an attribute
 * operation (not a state transition), performed as part of the combined ACKNOWLEDGE step.
 */
public enum OrderStatus {
    /** Order has been created but not yet accepted by the TP office. */
    CREATED,
    /** TP office has accepted the order and the assigned branch is set. */
    ACKNOWLEDGED,
    /** Vehicle and driver details have been confirmed (FTL: Vahan consent checked — BR-10). */
    FLEET_CONFIRMED,
    /** Vehicle has departed; cancellation is no longer allowed (BR-02). */
    IN_TRANSIT,
    /** Goods have been delivered; terminal state. */
    DELIVERED,
    /** Order was cancelled before transit started; reason stored in {@code cancelled_reason}. */
    CANCELLED
}
