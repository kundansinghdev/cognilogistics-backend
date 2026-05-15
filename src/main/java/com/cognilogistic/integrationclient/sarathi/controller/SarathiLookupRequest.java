package com.cognilogistic.integrationclient.sarathi.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/sarathi/lookup} (BACKEND_GAPS §12.2).
 *
 * <p>Order context required (not just the licence number) so the server can
 * verify the caller's TP account has consent for this specific order+DL pair
 * before forwarding to the Sarathi API.
 *
 * @param orderId   the order for which the licence is being verified
 * @param driverDl  driver licence number to look up; uppercased server-side
 */
public record SarathiLookupRequest(

        @NotNull String orderId,
        @NotBlank String driverDl
) {}
