package com.cognilogistic.order.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional request body for {@code POST /api/v1/orders/{id}/cancel}.
 *
 * <p>The entire body — including {@code reason} — is optional. When provided, the
 * reason is stored in {@code orders.cancelled_reason} for audit purposes.
 *
 * <p>BR-02 ("no cancel once IN_TRANSIT/DELIVERED") is enforced by the
 * {@link com.cognilogistic.order.statemachine.OrderStateMachine}, not on the DTO.
 *
 * @param reason a human-readable explanation for the cancellation (optional, max 500 chars
 *               to match the {@code order_status_log.notes} column width)
 */
public record CancelRequest(
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {}
