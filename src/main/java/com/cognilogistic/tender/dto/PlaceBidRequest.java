package com.cognilogistic.tender.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/v1/partner/tenders/{id}/bids}
 * (BACKEND_GAPS §7b).
 *
 * <p>The endpoint is upsert-style: if the partner already has a bid on this
 * tender, the existing row is updated in place (status reset to PENDING,
 * amount / notes overwritten). The DB UNIQUE on {@code (tender_id, partner_id)}
 * blocks inadvertent duplicate inserts.
 *
 * @param amountInr bid amount in INR (whole rupees); must be positive
 * @param etaDays   LP-promised days from pickup to delivery (optional)
 * @param notes     free-text bid context (optional, ≤500 chars)
 */
public record PlaceBidRequest(

        @NotNull
        @Positive
        Integer amountInr,

        Integer etaDays,

        String notes
) {}
