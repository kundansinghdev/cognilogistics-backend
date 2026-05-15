package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.OrderStatus;

import java.time.Instant;

/**
 * Read-model DTO for a single entry in the order status history (BR-06).
 *
 * <p>Every status transition — including the initial CREATED — is recorded as a
 * log row. The CREATED row has {@code fromStatus = null} because there is no
 * preceding state.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code id} — log entry primary key</li>
 *   <li>{@code fromStatus} — the status before the transition ({@code null} for CREATED)</li>
 *   <li>{@code toStatus} — the status after the transition</li>
 *   <li>{@code triggeredByUserId} — the user (TP or customer) who caused the change</li>
 *   <li>{@code triggeredAt} — when the transition occurred (set by JPA {@code @CreatedDate})</li>
 *   <li>{@code note} — optional human-readable note (e.g. cancellation reason)</li>
 * </ul>
 */
public record OrderStatusLogDto(
        String id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String triggeredByUserId,
        Instant triggeredAt,
        String note
) {}
