package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.OrderStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated metrics for the TP Orders dashboard — sourced exclusively from DB counts
 * (no client-side summation over the list payload).
 *
 * <p>{@link #statusCounts} always contains every {@link OrderStatus} key so the UI can
 * render status pills without null checks. {@link #allOrdersTotal} is the sum of those
 * counts (includes {@code CANCELLED}).
 *
 * @param statusCounts        count per lifecycle status for the applied filter scope
 * @param allOrdersTotal      sum of {@link #statusCounts} — matches "All" pill
 * @param inTransit           {@code IN_TRANSIT} count (card shortcut)
 * @param delivered           {@code DELIVERED} count (card shortcut)
 * @param expressTotal        orders with express / EXPRESS delivery in scope
 * @param expressPendingNew   express orders still in {@code CREATED} (BOEM "pending" sublabel)
 * @param conversionLast30d   delivered ÷ created in the rolling 30-day creation window
 */
public record OrderDashboardDto(
        Map<OrderStatus, Long> statusCounts,
        long allOrdersTotal,
        long inTransit,
        long delivered,
        long expressTotal,
        long expressPendingNew,
        OrderDashboardConversionDto conversionLast30d
) {

    /** Defensive copy so callers cannot mutate internal aggregation maps after build. */
    public OrderDashboardDto {
        Objects.requireNonNull(statusCounts, "statusCounts");
        Objects.requireNonNull(conversionLast30d, "conversionLast30d");
        statusCounts = Map.copyOf(statusCounts);
    }

    /**
     * Pre-populates every status with {@code 0L} before overlaying query rows.
     */
    public static Map<OrderStatus, Long> emptyStatusCounts() {
        Map<OrderStatus, Long> m = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            m.put(s, 0L);
        }
        return m;
    }
}
