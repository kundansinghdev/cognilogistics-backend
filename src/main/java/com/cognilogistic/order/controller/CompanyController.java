package com.cognilogistic.order.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.integrationclient.gst.GstLookupResponse;
import com.cognilogistic.order.dto.CompanyDto;
import com.cognilogistic.order.dto.CompanyMasterLookupResponse;
import com.cognilogistic.order.dto.CreateCompanyRequest;
import com.cognilogistic.order.service.CompanyService;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for company (shipper / consignee) master-data management
 * under {@code /api/v1/companies}.
 *
 * <p>Request logging follows the same {@code [ENTRY]} / {@code [EXIT]} pattern as
 * {@link com.cognilogistic.auth.controller.AuthController}.
 */
@Tag(name = "Companies", description = "Shipper/consignee master data + GSTIN lookup. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ApiResponse<List<CompanyDto>> search(@CurrentUser AuthPrincipal me,
                                                 @RequestParam(required = false) String search) {
        log.info("[ENTRY] searchCompanies | userId={} | searchPresent={}",
                me != null ? me.userId() : null, search != null && !search.isBlank());
        return ControllerRequestLogging.withExitLog(CompanyController.class, "searchCompanies",
                () -> companyService.search(me, search));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompanyDto> get(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] getCompany | id={} | userId={}", id, me != null ? me.userId() : null);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "getCompany",
                () -> companyService.get(me, id));
    }

    @PostMapping
    public ApiResponse<CompanyDto> create(@CurrentUser AuthPrincipal me,
                                          @Valid @RequestBody CreateCompanyRequest req) {
        log.info("[ENTRY] createCompany | userId={} | legalNameLen={}",
                me != null ? me.userId() : null,
                req.effectiveLegalName() != null ? req.effectiveLegalName().length() : 0);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "createCompany",
                () -> companyService.create(me, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CompanyDto> update(@CurrentUser AuthPrincipal me,
                                          @PathVariable String id,
                                          @Valid @RequestBody CreateCompanyRequest req) {
        log.info("[ENTRY] updateCompany | id={} | userId={}", id, me != null ? me.userId() : null);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "updateCompany",
                () -> companyService.update(me, id, req));
    }

    @PostMapping("/gstin-lookup")
    public ApiResponse<GstLookupResponse> gstinLookup(@CurrentUser AuthPrincipal me,
                                                      @RequestParam String gstin) {
        log.info("[ENTRY] gstinLookupPost | gstinLen={}", gstin != null ? gstin.length() : 0);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "gstinLookupPost",
                () -> companyService.gstinLookup(gstin).orElse(null));
    }

    @GetMapping("/lookup")
    public ApiResponse<GstLookupResponse> lookup(@CurrentUser AuthPrincipal me,
                                                @RequestParam String gstin) {
        log.info("[ENTRY] gstinLookupGet | gstinLen={}", gstin != null ? gstin.length() : 0);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "gstinLookupGet",
                () -> companyService.gstinLookup(gstin).orElse(null));
    }

    /**
     * Company Master lookup by GSTIN (TP-scoped). Returns id, legal name, and primary contact phone
     * for auto-fill on order creation — does not call the external GST SPI.
     */
    @GetMapping("/master-lookup")
    public ApiResponse<CompanyMasterLookupResponse> masterLookup(@CurrentUser AuthPrincipal me,
                                                                 @RequestParam String gstin) {
        log.info("[ENTRY] companyMasterLookup | gstinLen={}", gstin != null ? gstin.length() : 0);
        return ControllerRequestLogging.withExitLog(CompanyController.class, "companyMasterLookup",
                () -> companyService.lookupMasterByGstin(me, gstin).orElse(null));
    }
}
