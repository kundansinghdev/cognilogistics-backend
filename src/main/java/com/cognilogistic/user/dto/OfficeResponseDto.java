package com.cognilogistic.user.dto;

import java.time.Instant;

/**
 * Read-model DTO returned by all {@code /api/v1/offices} endpoints.
 *
 * <p>Fields mirror the {@code offices} table. {@code orderCount} is a derived value —
 * the total number of orders (any status) ever assigned to this office — computed at
 * query time by {@link com.cognilogistic.user.service.OfficeService}.
 *
 * @param id          database primary key
 * @param name        display name (e.g. "Faridabad Hub 1")
 * @param code        short mnemonic, always uppercase (e.g. "FB1")
 * @param city        city of the office location
 * @param state       state of the office location
 * @param pincode     postal code (nullable)
 * @param address     free-text street address (nullable)
 * @param gstin       branch-level GSTIN (nullable)
 * @param isActive    {@code true} if the office is operational; {@code false} if deactivated
 * @param orderCount  total orders ever assigned to this office (all statuses); equals
 *                    {@link OfficeOrderMetricsDto#totalOrders()} when {@code orderMetrics} is present
 * @param orderMetrics per-status breakdown + conversion bar — always non-null (zeros when no orders)
 * @param createdAt   timestamp of office creation
 */
public record OfficeResponseDto(
        String id,
        String name,
        String code,
        String city,
        String state,
        String pincode,
        String address,
        String gstin,
        boolean isActive,
        long orderCount,
        OfficeOrderMetricsDto orderMetrics,
        Instant createdAt
) {}
