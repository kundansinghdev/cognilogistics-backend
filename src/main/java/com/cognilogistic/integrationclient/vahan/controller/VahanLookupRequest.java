package com.cognilogistic.integrationclient.vahan.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/vahan/lookup}.
 *
 * <p>The order context is required (not just the vehicle registration) so the server can
 * verify the caller's TP account has consent for this specific order+vehicle pair before
 * forwarding the query to the Vahan API (BR-10 scope check).
 *
 * <p>Components:
 * <ul>
 *   <li>{@code orderId} — the order for which the vehicle is being verified</li>
 *   <li>{@code vehicleRegistration} — the Indian registration number to look up</li>
 * </ul>
 */
public record VahanLookupRequest(
        @NotNull String orderId,
        @NotBlank String vehicleRegistration
) {}
