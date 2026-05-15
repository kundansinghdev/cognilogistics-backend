package com.cognilogistic.tender.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/tenders/{id}/award} (BACKEND_GAPS §5.3).
 *
 * <p>The TP picks a winning bid. Service-side effects:
 * <ul>
 *   <li>Tender status: {@code IN_PROGRESS → COMPLETED}</li>
 *   <li>Winning bid: {@code PENDING → ACCEPTED} (wire-mapped to {@code AWARDED})</li>
 *   <li>All sibling bids on the same tender: {@code PENDING → REJECTED}</li>
 *   <li>{@code tenders.awarded_bid_id} = the chosen bid id</li>
 *   <li>{@code tenders.awarded_to} = the chosen partner id</li>
 * </ul>
 *
 * @param bidId UUID of the winning bid (must exist on this tender)
 */
public record TenderAwardRequest(@NotBlank String bidId) {}
