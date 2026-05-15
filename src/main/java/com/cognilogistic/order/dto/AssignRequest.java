package com.cognilogistic.order.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for assigning a branch office to an order.
 *
 * <p>Note: as of V3.6, this DTO may be superseded by the combined acknowledge flow
 * ({@link AcknowledgeRequest}). Office assignment is now typically done as part of
 * {@code POST /orders/{id}/acknowledge} rather than as a standalone call.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code officeId} — the ID of the branch office to assign</li>
 * </ul>
 */
public record AssignRequest(@NotNull String officeId) {}
