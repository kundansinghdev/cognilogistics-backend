package com.cognilogistic.order.model;

/**
 * Type of customer address. Stored as the enum's {@code name()} string in the
 * {@code customer_addresses.address_type} column (VARCHAR(20)). Discriminates the
 * different address roles a customer may have.
 *
 * <p>Indian B2B logistics commonly distinguishes:
 * <ul>
 *   <li><strong>Billing</strong> — registered office address; used on invoices.</li>
 *   <li><strong>Shipping</strong> — warehouse / dispatch site; used as the default
 *       pickup or drop location on orders.</li>
 *   <li><strong>Both</strong> — single address serving both purposes (common for SMEs).</li>
 * </ul>
 *
 * <p>A customer may have N billing and N shipping addresses; the
 * {@link com.cognilogistic.order.model.CustomerAddress#isDefault default} flag picks
 * the preferred one within each type for auto-fill on order creation.
 */
public enum AddressType {

    /** Registered / GST-printed address. Used on invoices. */
    BILLING,

    /** Operational address (warehouse / dispatch). Default pickup / drop location. */
    SHIPPING,

    /** Single address serving both roles. Common for small customers. */
    BOTH
}
