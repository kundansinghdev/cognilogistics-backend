package com.cognilogistic.order.service;

import com.cognilogistic.order.model.OrderStatus;

/**
 * Namespace class for order domain event records.
 *
 * <p>All event records are immutable (Java records with primitive / value-type fields)
 * and Kafka-ready. For UAT they are published in-process via Spring's
 * {@link org.springframework.context.ApplicationEventPublisher}. Post-UAT they will
 * be forwarded to Azure Event Hubs (Kafka API) without any change to the producer code.
 *
 * <p>This class is not instantiable — use the inner record types directly.
 */
public final class OrderEvents {

    /**
     * Published when a new order is created (status = CREATED, BR-06 initial log row).
     *
     * @param orderId     the newly created order ID
     * @param tpAccountId the owning TP account
     * @param customerId  the linked customer (may be a shadow customer, BR-04)
     * @param status      always {@code CREATED} at the time of publication
     */
    public record OrderCreated(String orderId, String tpAccountId, String customerId, OrderStatus status) {}

    /**
     * Published on every status transition after the initial CREATED.
     *
     * @param orderId           the order that changed status
     * @param from              the previous status
     * @param to                the new status
     * @param triggeredByUserId the user (TP or customer) who triggered the transition
     */
    public record OrderStatusChanged(String orderId, OrderStatus from, OrderStatus to, String triggeredByUserId) {}

    /**
     * Published when a Vahan vehicle registry lookup is initiated (for observability / audit).
     *
     * @param orderId             the order for which the lookup is being performed
     * @param vehicleRegistration the registration number being looked up
     * @param mockMode            {@code true} when the mock Vahan client is active (non-production)
     */
    public record VahanLookupRequested(String orderId, String vehicleRegistration, boolean mockMode) {}

    private OrderEvents() {}
}
