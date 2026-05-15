package com.cognilogistic.user.dto;

/**
 * DB-sourced order KPIs for a branch office card (list view). Mirrors the client-side
 * aggregation previously done in {@code BranchesListPage} — totals are computed on the server.
 *
 * @param totalOrders              orders with {@code assigned_office_id} = this office (all statuses)
 * @param inTransitOrFleetConfirmed {@code IN_TRANSIT} + {@code FLEET_CONFIRMED} (BOEM "committed / moving" bucket)
 * @param delivered                {@code DELIVERED} count
 * @param express                    {@code is_express} OR {@code delivery_type = EXPRESS}
 * @param conversionRatePercent      {@code 100 * delivered / total}, {@code 0} when {@code totalOrders == 0}
 */
public record OfficeOrderMetricsDto(
        long totalOrders,
        long inTransitOrFleetConfirmed,
        long delivered,
        long express,
        double conversionRatePercent
) {
    public static OfficeOrderMetricsDto empty() {
        return new OfficeOrderMetricsDto(0, 0, 0, 0, 0.0);
    }
}
