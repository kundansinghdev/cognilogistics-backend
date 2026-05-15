package com.cognilogistic.reports.dto;

import com.cognilogistic.user.model.Plan;

/**
 * Wire response for {@code GET /api/v1/reports/plan-usage}.
 *
 * <p>UAT scope is intentionally minimal — current month tender count vs the plan's
 * monthly cap. Post-UAT additions: projected exhaustion date, history of past months,
 * peak-day chart.
 *
 * @param tpAccountId the TP this usage covers
 * @param plan        the TP's plan tier (BASIC / PREMIUM / ENTERPRISE)
 * @param monthlyTenderCap monthly tender cap for the plan; {@code null} for unlimited
 *                         (PREMIUM / ENTERPRISE in BR-PLN-03)
 * @param tendersThisMonth tenders created in the current calendar month for the TP
 */
public record PlanUsageReportDto(
        String tpAccountId,
        Plan plan,
        Integer monthlyTenderCap,
        long tendersThisMonth) {
}
