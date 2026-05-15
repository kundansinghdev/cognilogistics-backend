package com.cognilogistic.reports.service;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.reports.dto.OrderStatusCountsDto;
import com.cognilogistic.reports.dto.PlanUsageReportDto;
import com.cognilogistic.reports.dto.TenderConversionDto;
import com.cognilogistic.user.model.Plan;
import com.cognilogistic.user.model.TpAccount;
import com.cognilogistic.user.repository.TpAccountJpa;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Top-level facade the {@code ReportController} talks to. Handles the cross-cutting
 * concerns shared by every report endpoint:
 *
 * <ol>
 *   <li><strong>Plan gate</strong> — ENTERPRISE-only access (notification.md §3.2).
 *       BASIC / PREMIUM TPs get 403 {@link ErrorCode#PLAN_UPGRADE_REQUIRED}. The full
 *       {@code PlanAccessService} is a user-module follow-up; for UAT we hard-code the
 *       plan check against the seeded ENTERPRISE TP (Bhoomihaar).</li>
 *   <li><strong>Date-range defaults</strong> — when {@code from} / {@code to} are
 *       absent, default to the last 30 days (open question §11.5, default).</li>
 * </ol>
 *
 * <p>Each method on this service is a thin orchestrator over the corresponding
 * metric service ({@link OrderMetricsService}, {@link TenderMetricsService},
 * {@link PlanUsageReportService}).
 */
@Service
public class ReportService {

    /** Default date-range window when no {@code from}/{@code to} are supplied. */
    private static final long DEFAULT_RANGE_DAYS = 30;

    /** Maximum allowed range so a runaway query doesn't scan a year of orders. */
    private static final long MAX_RANGE_DAYS = 365;

    private final OrderMetricsService orderMetricsService;
    private final TenderMetricsService tenderMetricsService;
    private final PlanUsageReportService planUsageReportService;
    private final TpAccountJpa tpAccountJpa;

    public ReportService(OrderMetricsService orderMetricsService,
                         TenderMetricsService tenderMetricsService,
                         PlanUsageReportService planUsageReportService,
                         TpAccountJpa tpAccountJpa) {
        this.orderMetricsService = orderMetricsService;
        this.tenderMetricsService = tenderMetricsService;
        this.planUsageReportService = planUsageReportService;
        this.tpAccountJpa = tpAccountJpa;
    }

    /**
     * Order status-counts for the caller's TP, optionally filtered by office.
     *
     * <p>For TP_TRANSPORT_MANAGER we should restrict to the user's assigned offices
     * (notification.md §11.2). Until {@code user_office_assignments} lookups are
     * available cross-module, the controller passes the user's requested
     * {@code officeId} through as-is and we trust the front-end to scope correctly.
     */
    public OrderStatusCountsDto orderStatusCounts(AuthPrincipal me, Instant from, Instant to, String officeId) {
        gateAccess(me);
        Instant[] range = applyDefaults(from, to);
        List<Object[]> rows = orderMetricsService.countByStatus(me.tpAccountId(), range[0], range[1], officeId);
        return OrderStatusCountsDto.from(me.tpAccountId(), officeId, rows);
    }

    /** Tender conversion rate over the date range. */
    public TenderConversionDto tenderConversion(AuthPrincipal me, Instant from, Instant to) {
        gateAccess(me);
        Instant[] range = applyDefaults(from, to);
        Object[] funnel = tenderMetricsService.funnelCounts(me.tpAccountId(), range[0], range[1]);
        long total = funnel[0] == null ? 0 : ((Number) funnel[0]).longValue();
        long awarded = funnel[1] == null ? 0 : ((Number) funnel[1]).longValue();
        long cancelled = funnel[2] == null ? 0 : ((Number) funnel[2]).longValue();
        return TenderConversionDto.compute(me.tpAccountId(), total, awarded, cancelled);
    }

    /**
     * Average order delivery time in seconds. Returns the value wrapped in a small
     * record so the JSON shape is stable even when no orders qualify (returns 0).
     *
     * @return seconds from creation to delivery
     */
    public AvgDeliverDto avgDeliverTime(AuthPrincipal me, Instant from, Instant to) {
        gateAccess(me);
        Instant[] range = applyDefaults(from, to);
        Double secs = orderMetricsService.avgDeliverSeconds(me.tpAccountId(), range[0], range[1]);
        return new AvgDeliverDto(me.tpAccountId(), secs == null ? 0.0 : secs);
    }

    /** Current-month plan usage. */
    public PlanUsageReportDto planUsage(AuthPrincipal me) {
        // Plan gate doesn't apply to plan-usage itself — every TP_ADMIN should see their
        // own quota. We still enforce that the caller is TP_ADMIN (not transport manager)
        // because the plan info is administrative.
        if (me.role() != UserRole.TP_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only TP_ADMIN can view plan usage.");
        }
        return planUsageReportService.currentMonth(me.tpAccountId());
    }

    /**
     * Wire shape for the average-deliver-time report. Inline record to keep the
     * one-off DTO close to the producer.
     */
    public record AvgDeliverDto(String tpAccountId, double avgDeliverSeconds) { }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Plan / role gate for the three "real reports" endpoints. UAT hard-codes the
     * check rather than going through a {@code PlanAccessService} that doesn't exist
     * yet. When that service lands (post-UAT), this method becomes a single delegating
     * call.
     */
    private void gateAccess(AuthPrincipal me) {
        // Role: TP_ADMIN or TP_TRANSPORT_MANAGER on the same TP. Other roles → forbidden.
        if (me.role() != UserRole.TP_ADMIN && me.role() != UserRole.TP_TRANSPORT_MANAGER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Reports access requires a TP role.");
        }
        if (me.tpAccountId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Reports require a TP context.");
        }
        // Plan: ENTERPRISE only (notification.md §3.2 — Reports row in the access matrix).
        TpAccount tp = tpAccountJpa.findById(me.tpAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "TP account not found."));
        if (tp.getPlan() != Plan.ENTERPRISE) {
            throw new ApiException(ErrorCode.PLAN_UPGRADE_REQUIRED,
                    "Reports module requires the ENTERPRISE plan.");
        }
    }

    /**
     * Substitutes default range bounds when callers omit them, and clamps total
     * range to {@link #MAX_RANGE_DAYS} so a malformed request can't sweep an entire
     * decade of data.
     */
    private static Instant[] applyDefaults(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant resolvedTo = to == null ? now : to;
        Instant resolvedFrom = from == null
                ? resolvedTo.atZone(ZoneOffset.UTC).toLocalDate().minusDays(DEFAULT_RANGE_DAYS)
                .atStartOfDay().toInstant(ZoneOffset.UTC)
                : from;

        long days = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo);
        if (days > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Date range exceeds maximum of " + MAX_RANGE_DAYS + " days.");
        }
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Date range 'to' must be ≥ 'from'.");
        }
        return new Instant[]{resolvedFrom, resolvedTo};
    }
}
