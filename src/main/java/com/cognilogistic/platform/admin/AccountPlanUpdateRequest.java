package com.cognilogistic.platform.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/v1/admin/accounts/{id}/plan} (BACKEND_GAPS §7).
 *
 * @param plan one of {@code "BASIC"} / {@code "PREMIUM"} / {@code "ENTERPRISE"};
 *             validated against the {@code Plan} enum at the service layer
 */
public record AccountPlanUpdateRequest(@NotBlank String plan) {}
