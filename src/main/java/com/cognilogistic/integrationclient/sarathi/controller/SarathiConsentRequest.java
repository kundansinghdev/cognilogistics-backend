package com.cognilogistic.integrationclient.sarathi.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/sarathi/consent} (BACKEND_GAPS §12.2).
 *
 * <p>Mirrors the Vahan consent-recording shape. Implicit-consent UX: the FE's
 * "Run Sarthi check" click is the affirmative action (no separate checkbox);
 * the FE always submits {@code consentGiven=true}.
 *
 * @param orderId       order context for the consent (NOT NULL on the schema today;
 *                      future iterations may allow null for driver-onboarding flows)
 * @param driverDl      driver licence number; uppercased server-side before save
 * @param consentText   full text of the consent statement shown to the user (audit trail)
 * @param consentGiven  {@code true} when the TP user accepted; {@code false} on decline
 */
public record SarathiConsentRequest(

        @NotNull String orderId,
        @NotBlank String driverDl,
        @NotBlank String consentText,
        boolean consentGiven
) {}
