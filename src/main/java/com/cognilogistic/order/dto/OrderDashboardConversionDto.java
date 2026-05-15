package com.cognilogistic.order.dto;

/**
 * Rolling-window conversion for the dashboard bar — orders <strong>created</strong> in the
 * window vs those that reached {@code DELIVERED} (among orders created in the window).
 *
 * @param windowDays           rolling look-back (default 30)
 * @param ordersCreatedInWindow denominator — orders whose {@code createdAt} falls in the window
 * @param deliveredInWindow    numerator — subset of the above with current status {@code DELIVERED}
 * @param ratePercent          {@code 100.0 * deliveredInWindow / ordersCreatedInWindow}, or {@code 0} if denominator is 0
 */
public record OrderDashboardConversionDto(
        int windowDays,
        long ordersCreatedInWindow,
        long deliveredInWindow,
        double ratePercent
) {}
