package com.cognilogistic.tender.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/tenders} — creates a new tender for PTL order consolidation.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code description} — human-readable description of the tender (required)</li>
 *   <li>{@code orderIds} — optional list of PTL order IDs to immediately consolidate into this tender;
 *       each order must be in ACKNOWLEDGED or FLEET_CONFIRMED status and of type PTL;
 *       when omitted or empty, an empty tender in DRAFT status is created</li>
 * </ul>
 */
public record CreateTenderRequest(
        @NotNull String description,
        List<String> orderIds  // optional — only for PTL order consolidation
) {}
