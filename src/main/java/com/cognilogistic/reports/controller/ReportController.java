package com.cognilogistic.reports.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.reports.dto.OrderStatusCountsDto;
import com.cognilogistic.reports.dto.PlanUsageReportDto;
import com.cognilogistic.reports.dto.TenderConversionDto;
import com.cognilogistic.reports.service.ReportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for the Reports module — read-only aggregations over orders,
 * tenders, and plan usage.
 *
 * <p>Mounted at {@code /api/v1/reports/*}. Plan / role gating happens inside
 * {@link ReportService#orderStatusCounts}, {@link ReportService#tenderConversion},
 * {@link ReportService#avgDeliverTime}, and {@link ReportService#planUsage} so all
 * gate logic stays in one place.
 *
 * <p><strong>UAT scope (reports.md §1):</strong> three "real reports" endpoints +
 * one plan-usage endpoint. Trend charts, CSV export, cross-tenant admin reports —
 * all post-UAT.
 */
@Tag(name = "Reports", description = "Read-only TP analytics. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Order status counts for the caller's TP, optionally filtered by branch office
     * and date range. Defaults: last 30 days, all offices.
     *
     * @param me       the authenticated principal
     * @param from     ISO-8601 instant lower bound (inclusive); null = 30 days before {@code to}
     * @param to       ISO-8601 instant upper bound (exclusive); null = now
     * @param officeId optional branch office filter
     */
    @GetMapping("/orders/status-counts")
    public ApiResponse<OrderStatusCountsDto> orderStatusCounts(@CurrentUser AuthPrincipal me,
                                                               @RequestParam(name = "from", required = false) Instant from,
                                                               @RequestParam(name = "to", required = false) Instant to,
                                                               @RequestParam(name = "officeId", required = false) String officeId) {
        return ApiResponse.ok(reportService.orderStatusCounts(me, from, to, officeId));
    }

    /**
     * Tender conversion (awarded / total) over the date range. Defaults: last 30 days.
     */
    @GetMapping("/tenders/conversion")
    public ApiResponse<TenderConversionDto> tenderConversion(@CurrentUser AuthPrincipal me,
                                                             @RequestParam(name = "from", required = false) Instant from,
                                                             @RequestParam(name = "to", required = false) Instant to) {
        return ApiResponse.ok(reportService.tenderConversion(me, from, to));
    }

    /**
     * Average order delivery time (creation → DELIVERED) in seconds.
     */
    @GetMapping("/orders/avg-deliver-time")
    public ApiResponse<ReportService.AvgDeliverDto> avgDeliverTime(@CurrentUser AuthPrincipal me,
                                                                   @RequestParam(name = "from", required = false) Instant from,
                                                                   @RequestParam(name = "to", required = false) Instant to) {
        return ApiResponse.ok(reportService.avgDeliverTime(me, from, to));
    }

    /**
     * Current-month plan usage for the caller's TP. TP_ADMIN only — TP_TRANSPORT_MANAGER
     * cannot read plan info.
     */
    @GetMapping("/plan-usage")
    public ApiResponse<PlanUsageReportDto> planUsage(@CurrentUser AuthPrincipal me) {
        return ApiResponse.ok(reportService.planUsage(me));
    }
}
