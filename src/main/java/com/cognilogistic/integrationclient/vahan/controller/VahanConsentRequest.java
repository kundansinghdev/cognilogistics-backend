package com.cognilogistic.integrationclient.vahan.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/vahan/consent} — records the TP user's
 * Vahan consent decision for a specific order+vehicle before fleet confirmation.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code orderId} — the order for which consent is being captured</li>
 *   <li>{@code vehicleRegistration} — the vehicle the consent applies to;
 *       must match the registration used at FLEET_CONFIRMED (BR-10)</li>
 *   <li>{@code consentText} — the full consent statement text shown to the user (audit trail)</li>
 *   <li>{@code consentGiven} — {@code true} if the TP user agreed; {@code false} if declined
 *       (declined consent logs are stored but will block fleet confirmation)</li>
 * </ul>
 */
public record VahanConsentRequest(
        @NotNull String orderId,
        @NotBlank String vehicleRegistration,
        @NotBlank String consentText,
        boolean consentGiven
) {}
