package com.cognilogistic.integrationclient.vahan.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.integrationclient.vahan.VahanLookupResponse;
import com.cognilogistic.integrationclient.vahan.VahanService;
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
 * REST controller for Vahan (MoRTH vehicle registry) consent and lookup endpoints
 * under {@code /api/v1/vahan}.
 *
 * <p>These endpoints are used exclusively by authenticated TP users before confirming
 * an FTL order's fleet assignment. The required sequence is:
 * <ol>
 *   <li>POST /vahan/consent — record the user's consent decision</li>
 *   <li>POST /vahan/lookup — retrieve vehicle details (consent must exist first)</li>
 *   <li>POST /orders/{id}/confirm-fleet — complete fleet confirmation (BR-10 validated here)</li>
 * </ol>
 */
@Tag(name = "Vahan", description = "Vehicle registry consent + lookup (FTL). JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/vahan")
public class VahanController {

    private final VahanService service;

    public VahanController(VahanService service) {
        this.service = service;
    }

    /**
     * Records the authenticated TP user's Vahan consent for a specific order+vehicle combination.
     *
     * @param me  the authenticated TP user
     * @param req consent details including orderId, vehicleRegistration, consentText, and consentGiven flag
     * @return empty success wrapper
     */
    @PostMapping("/consent")
    public ApiResponse<java.util.Map<String, Object>> consent(@CurrentUser AuthPrincipal me,
                                                              @Valid @RequestBody VahanConsentRequest req) {
        service.recordConsent(me, req.orderId(), req.vehicleRegistration(), req.consentText(), req.consentGiven());
        // Non-empty data so the FE's res.data.data is a real object.
        return ApiResponse.ok(java.util.Map.of("recorded", true));
    }

    /**
     * Looks up vehicle details from the Vahan registry. Requires consent to have been
     * recorded first for this order+vehicle combination (BR-10).
     *
     * @param me  the authenticated TP user
     * @param req lookup parameters (orderId, vehicleRegistration)
     * @return the Vahan registry data for the specified vehicle
     */
    @PostMapping("/lookup")
    public ApiResponse<VahanLookupResponse> lookup(@CurrentUser AuthPrincipal me,
                                                   @Valid @RequestBody VahanLookupRequest req) {
        return ApiResponse.ok(service.lookup(me, req.orderId(), req.vehicleRegistration()));
    }
}
