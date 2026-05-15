package com.cognilogistic.order.model;

/**
 * Delivery speed tier for an order. Stored as the enum's {@code name()} string in the
 * {@code orders.delivery_type} column (VARCHAR(10)).
 *
 * <p>The {@link com.cognilogistic.order.model.Order#isExpress} flag is a denormalised
 * cache of {@code delivery_type == EXPRESS} so simple dashboard queries don't have to
 * compute the comparison; the application keeps the two in sync on every write.
 *
 * <p>For UAT: {@link #EXPRESS} is cosmetic — it surfaces a "🚀 Express" badge in the UI
 * and may inform office-suggestion preference (BR-ORD-13). Post-UAT, EXPRESS may unlock
 * priority routing in the tender broadcast logic.
 */
public enum DeliveryType {

    /** Default delivery speed. Standard SLA. */
    NORMAL,

    /** Priority delivery. Surfaces a badge in the UI; may unlock priority routing post-UAT. */
    EXPRESS
}
