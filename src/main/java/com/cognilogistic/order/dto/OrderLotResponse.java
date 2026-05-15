package com.cognilogistic.order.dto;

import java.util.List;

/**
 * Response shape for {@code GET /api/v1/orders/{id}/lot}.
 *
 * <p>A "connected lot" is the set of orders in the caller's TP that share the same
 * vehicle registration and pickup date as the target order. The order-detail page
 * renders a multi-order panel when this list has 2+ entries, and suppresses it for
 * a singleton — but the API always returns at least the target order so the FE
 * can use the same shape unconditionally.
 *
 * @param lotId  stable identifier for the lot (composed of vehicle number + pickup
 *               date when a real lot exists, otherwise the order id for singletons)
 * @param orders the orders in the lot, ascending by {@code createdAt}
 */
public record OrderLotResponse(String lotId, List<OrderDto> orders) {}
