package com.cognilogistic.reports.dto;

import com.cognilogistic.order.model.OrderStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Wire response for {@code GET /api/v1/reports/orders/status-counts}.
 *
 * <p>The {@link #counts} map carries one entry per {@link OrderStatus} so the client
 * doesn't need a fallback for missing statuses — every status is present, even if the
 * count is zero. {@link #total} is the sum of the map values, included for convenience
 * so the client doesn't have to re-sum.
 *
 * @param tpAccountId the TP whose orders these counts cover (echoed back for the client)
 * @param officeId    the office filter that was applied, or {@code null} for TP-wide
 * @param counts      one entry per status; entries with zero count are still present
 * @param total       sum of all counts in {@link #counts}
 */
public record OrderStatusCountsDto(
        String tpAccountId,
        String officeId,
        Map<OrderStatus, Long> counts,
        long total) {

    /**
     * Builds the DTO with every status pre-populated to zero, then overlays the
     * supplied (status, count) pairs. Guarantees the client sees the full status set
     * even for an empty TP.
     */
    public static OrderStatusCountsDto from(String tpAccountId, String officeId, List<Object[]> rows) {
        // EnumMap so iteration order matches the enum declaration order — predictable for the UI.
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        long total = 0;
        for (Object[] row : rows) {
            // The query returns (status, count) tuples — order matches the SELECT clause in the repo.
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status, count);
            total += count;
        }
        return new OrderStatusCountsDto(tpAccountId, officeId, counts, total);
    }
}
