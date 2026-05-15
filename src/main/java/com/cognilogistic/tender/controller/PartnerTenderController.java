package com.cognilogistic.tender.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.tender.dto.AssignTenderRequest;
import com.cognilogistic.tender.dto.PlaceBidRequest;
import com.cognilogistic.tender.dto.TenderDto;
import com.cognilogistic.tender.service.PartnerTenderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Partner Portal under {@code /api/v1/partner/tenders}
 * (BACKEND_GAPS §7b).
 *
 * <p>All endpoints require {@code PARTNER_TP} role; the visibility filter
 * (broadcastGroups ∩ caller's groups) is enforced inside
 * {@link PartnerTenderService}. Direct id access for tenders not broadcast to
 * the partner returns {@code ORDER_NOT_FOUND} rather than {@code FORBIDDEN}, to
 * avoid disclosing tender existence cross-tenant.
 */
@Tag(name = "Tenders (Partner)", description = "PARTNER_TP. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/partner/tenders")
public class PartnerTenderController {

    private final PartnerTenderService partnerService;

    public PartnerTenderController(PartnerTenderService partnerService) {
        this.partnerService = partnerService;
    }

    /** Lists tenders broadcast to a group containing the caller's partner profile. */
    @GetMapping
    public ApiResponse<List<TenderDto>> list(@CurrentUser AuthPrincipal me) {
        return ApiResponse.ok(partnerService.listVisible(me));
    }

    /**
     * Places (or revises) a bid (BACKEND_GAPS §7b). Idempotent at the
     * (tender, partner) level — replaces any prior PENDING bid by the same partner.
     */
    @PostMapping("/{id}/bids")
    public ApiResponse<TenderDto> placeBid(@CurrentUser AuthPrincipal me,
                                            @PathVariable String id,
                                            @Valid @RequestBody PlaceBidRequest req) {
        return ApiResponse.ok(partnerService.placeBid(me, id, req));
    }

    /**
     * Submits the winning partner's vehicle + driver after award. Locks once
     * written — re-submission attempts fail with {@code INVALID_TRANSITION}.
     */
    @PostMapping("/{id}/assign")
    public ApiResponse<TenderDto> assign(@CurrentUser AuthPrincipal me,
                                          @PathVariable String id,
                                          @Valid @RequestBody AssignTenderRequest req) {
        return ApiResponse.ok(partnerService.assign(me, id, req));
    }
}
