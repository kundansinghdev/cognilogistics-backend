package com.cognilogistic.tender.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.tender.dto.CreateTenderRequest;
import com.cognilogistic.tender.dto.TenderAwardRequest;
import com.cognilogistic.tender.dto.TenderBroadcastRequest;
import com.cognilogistic.tender.dto.TenderDto;
import com.cognilogistic.tender.service.TenderService;
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
 * REST controller for tender management at {@code /api/v1/tenders}.
 *
 * <p>R4 endpoints (BACKEND_GAPS §5):
 * <ul>
 *   <li>{@code GET    /tenders}                — list TP's tenders</li>
 *   <li>{@code GET    /tenders/{id}}           — single tender with full bid + broadcast context</li>
 *   <li>{@code POST   /tenders}                — create draft tender (with optional PTL consolidation)</li>
 *   <li>{@code POST   /tenders/{id}/broadcast} — DRAFT → IN_PROGRESS, append channels, write group rows</li>
 *   <li>{@code POST   /tenders/{id}/award}     — IN_PROGRESS → COMPLETED, award winning bid, reject siblings</li>
 * </ul>
 *
 * <p>Partner-side surface ({@code POST /partner/tenders/{id}/bids} +
 * {@code /assign}) is in PR R7.
 */
@Tag(name = "Tenders (TP)", description = "TP tender lifecycle. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/tenders")
public class TenderController {

    private final TenderService tenderService;

    public TenderController(TenderService tenderService) {
        this.tenderService = tenderService;
    }

    @GetMapping
    public ApiResponse<List<TenderDto>> list(@CurrentUser AuthPrincipal me) {
        return ApiResponse.ok(tenderService.list(me));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenderDto> get(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        return ApiResponse.ok(tenderService.get(me, id));
    }

    @PostMapping
    public ApiResponse<TenderDto> create(@CurrentUser AuthPrincipal me,
                                         @Valid @RequestBody CreateTenderRequest req) {
        return ApiResponse.ok(tenderService.create(me, req));
    }

    /**
     * Broadcasts a tender (BACKEND_GAPS §5.2). Two channels share this endpoint —
     * see {@link TenderBroadcastRequest} for the per-channel field requirements.
     */
    @PostMapping("/{id}/broadcast")
    public ApiResponse<TenderDto> broadcast(@CurrentUser AuthPrincipal me,
                                             @PathVariable String id,
                                             @Valid @RequestBody TenderBroadcastRequest req) {
        return ApiResponse.ok(tenderService.broadcast(me, id, req));
    }

    /**
     * Awards a tender (BACKEND_GAPS §5.3). Validates bid ownership + tender status,
     * then performs the win/lose status fan-out across all bids on the tender.
     */
    @PostMapping("/{id}/award")
    public ApiResponse<TenderDto> award(@CurrentUser AuthPrincipal me,
                                         @PathVariable String id,
                                         @Valid @RequestBody TenderAwardRequest req) {
        return ApiResponse.ok(tenderService.award(me, id, req));
    }
}
