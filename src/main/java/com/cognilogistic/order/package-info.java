/**
 * Order module — the heart of the V3.6 order-management backend.
 *
 * <p>Owns: orders, customers, companies, order_status_log, connected_lots,
 * GR/LR document generation, and the customer portal services
 * ({@code order.service.portal}). Implements the
 * {@code CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED}
 * lifecycle and the audit log of every transition (BR-06).
 *
 * <p>Depends on: {@code auth} (for {@link com.cognilogistic.auth.security.AuthPrincipal}),
 * {@code user} (for offices and TP-account scoping), and {@code platform.api}.
 * Does NOT depend on {@code integrationclient} — the Vahan consent check is
 * plugged in via the {@code OrderService.FleetConfirmGuard} SPI instead.
 * Depended on by: {@code integrationclient.vahan} (which implements the SPI),
 * {@code tender}, {@code reports}, and the customer portal.
 *
 * <p>Read first: {@link com.cognilogistic.order.service.OrderService} (canonical
 * multi-tenant CRUD + transitions) and
 * {@link com.cognilogistic.order.statemachine.OrderStateMachine} (BR-01, BR-02).
 */
package com.cognilogistic.order;
