package com.cognilogistic.order.model;

/**
 * Role of a customer contact. Stored as the enum's {@code name()} string in the
 * {@code customer_contacts.contact_type} column (VARCHAR(20)). Drives notification
 * routing — different events go to different contact types.
 *
 * <p>Notification routing convention:
 * <ul>
 *   <li>Order status updates → {@link #PRIMARY}</li>
 *   <li>Invoices / payment reminders → {@link #FINANCE}</li>
 *   <li>Pickup / drop / dispatch coordination → {@link #LOGISTICS}</li>
 * </ul>
 *
 * <p>If a contact type isn't populated, notifications fall back to the customer's
 * {@link #PRIMARY} contact. A row marked {@code is_primary=TRUE} (any type) is
 * always reachable.
 */
public enum ContactType {

    /** Main day-to-day contact. Default fallback for all notification routing. */
    PRIMARY,

    /** Accounts / billing. Receives invoices and payment reminders. */
    FINANCE,

    /** Logistics / dispatch. Receives pickup / drop coordination updates. */
    LOGISTICS
}
