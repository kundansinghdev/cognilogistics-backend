package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/check-phone}.
 *
 * @param phone E.164 Indian mobile (same as other auth endpoints).
 */
public record CheckPhoneRequest(@NotBlank String phone) {}
