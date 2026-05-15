package com.cognilogistic.tender.dto;

import java.time.Instant;

/**
 * Wire DTO for a single bid on a tender (BACKEND_GAPS §5.4 / §7b).
 *
 * <p><strong>Status mapping note:</strong> the entity's {@code BidStatus} enum
 * uses {@code ACCEPTED} (matching the schema), but the front-end's wire shape
 * uses {@code AWARDED}. The service translates {@code ACCEPTED → "AWARDED"} when
 * building this DTO so the FE doesn't need a per-status mapping table. Keeps
 * the schema column unchanged (the canonical name there is still ACCEPTED).
 *
 * @param id          bid UUID
 * @param tenderId    parent tender id
 * @param partnerId   FK {@code partner_tp_profiles.id} of the bidder
 * @param partnerName denormalised display name for the partner (avoids an extra fetch on the FE)
 * @param amountInr   bid amount in INR (whole rupees)
 * @param etaDays     LP-promised days from pickup to delivery; null if not committed
 * @param notes       free-text bid context, optional
 * @param status      one of {@code "PENDING"} / {@code "AWARDED"} / {@code "REJECTED"} / {@code "WITHDRAWN"}
 * @param submittedAt creation timestamp (also used as {@code placedAt} per BACKEND_GAPS §7b)
 */
public record BidDto(
        String id,
        String tenderId,
        String partnerId,
        String partnerName,
        Integer amountInr,
        Integer etaDays,
        String notes,
        String status,
        Instant submittedAt
) {}
