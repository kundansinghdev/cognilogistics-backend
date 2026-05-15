package com.cognilogistic.platform.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/v1/admin/accounts/{id}/status} (BACKEND_GAPS §7).
 *
 * @param status one of {@code "APPROVED"} / {@code "REJECTED"} / {@code "PENDING"};
 *               validated against the {@code AccountStatus} enum at the service layer
 */
public record AccountStatusUpdateRequest(@NotBlank String status) {}
