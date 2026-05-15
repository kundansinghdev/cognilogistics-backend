package com.cognilogistic.user.controller;

import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.user.dto.PartnerDto;
import com.cognilogistic.user.service.PartnerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the partners directory at {@code /api/v1/partners}
 * (BACKEND_GAPS §6).
 *
 * <p>Open to any authenticated TP user — the partner directory is read-only and
 * not tenant-private. Mutations (adding partners) flow through the admin /
 * impersonation surface in PR R6, not from here.
 */
@Tag(name = "Partners (directory)", description = "Read-only partner list for TP users. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final PartnerService partners;

    public PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    /**
     * Lists every Logistics Partner the platform knows about, ordered by company name.
     * R5 returns the global directory; future iterations may scope by
     * {@code tp_partner_network} once that table is wired up.
     */
    @GetMapping
    public ApiResponse<List<PartnerDto>> list() {
        return ApiResponse.ok(partners.list());
    }
}
