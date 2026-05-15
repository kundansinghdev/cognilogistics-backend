package com.cognilogistic.integrationclient.sarathi.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.integrationclient.sarathi.SarathiLookupResponse;
import com.cognilogistic.integrationclient.sarathi.SarathiService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Sarathi (driver-licence) consent and lookup at
 * {@code /api/v1/sarathi/*} (BACKEND_GAPS §12.2).
 *
 * <p>Mirror of {@link com.cognilogistic.integrationclient.vahan.controller.VahanController}.
 * The required sequence:
 *
 * <ol>
 *   <li>{@code POST /sarathi/consent} — record the user's consent (the FE
 *       fires this implicitly on the "Run Sarthi check" click).</li>
 *   <li>{@code POST /sarathi/lookup} — fetch driver-licence details (consent must
 *       exist first).</li>
 *   <li>{@code POST /orders/{id}/confirm-fleet} — service uses {@code driverDl}
 *       to resolve the matching consent (gate relaxed via
 *       {@code orders.fleet.require-vahan-consent} for UAT).</li>
 * </ol>
 */
@Tag(name = "Sarathi", description = "Driver licence consent + lookup. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/sarathi")
public class SarathiController {

    private final SarathiService service;

    public SarathiController(SarathiService service) {
        this.service = service;
    }

    /** Records the TP user's consent for a Sarathi lookup on the given order + DL. */
    @PostMapping("/consent")
    public ApiResponse<Void> consent(@CurrentUser AuthPrincipal me,
                                     @Valid @RequestBody SarathiConsentRequest req) {
        service.recordConsent(me, req.orderId(), req.driverDl(), req.consentText(), req.consentGiven());
        return ApiResponse.ok();
    }

    /** Looks up a driver licence in the Sarathi registry; requires prior consent. */
    @PostMapping("/lookup")
    public ApiResponse<SarathiLookupResponse> lookup(@CurrentUser AuthPrincipal me,
                                                     @Valid @RequestBody SarathiLookupRequest req) {
        return ApiResponse.ok(service.lookup(me, req.orderId(), req.driverDl()));
    }
}
