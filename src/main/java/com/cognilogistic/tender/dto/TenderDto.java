package com.cognilogistic.tender.dto;

import com.cognilogistic.tender.model.TenderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire DTO for a tender (BACKEND_GAPS §5.1).
 *
 * <p>Mirrors the front-end's {@code Tender} TypeScript type. Carries the full
 * broadcast / award context the FE needs to render the tender list, detail, and
 * partner-portal views without follow-up fetches:
 * <ul>
 *   <li>{@link #orderIds} — PTL orders consolidated under this tender</li>
 *   <li>{@link #broadcastGroupIds} — partner_groups this tender was broadcast to</li>
 *   <li>{@link #broadcastPartnerCount} — cached count of distinct partners reached</li>
 *   <li>{@link #sentVia} — channels broadcast was dispatched through (subset of {@code ["app", "whatsapp"]})</li>
 *   <li>{@link #bidsCount} — number of bids placed</li>
 *   <li>{@link #bids} — full bid list (always populated for owners; empty list for non-owners)</li>
 *   <li>{@link #awardedBidId} — winning bid id when {@link #status} is COMPLETED</li>
 * </ul>
 *
 * @param id                     tender UUID
 * @param tpAccountId            owning TP
 * @param tenderNumber           human-readable number, e.g. {@code TND-20260508-0001}
 * @param status                 lifecycle status
 * @param title                  optional title
 * @param origin                 free-text origin
 * @param destination            free-text destination
 * @param vehicleType            requested vehicle type
 * @param goodsType              goods category
 * @param pickupDate             pickup date
 * @param deliveryDate           expected delivery date
 * @param refPriceInr            TP reference price in INR (advisory, shown to bidders)
 * @param notes                  free-text notes
 * @param orderIds               PTL orders consolidated into this tender
 * @param broadcastGroupIds      partner_groups this tender was broadcast to
 * @param broadcastPartnerCount  cached count of distinct partners reached
 * @param sentVia                broadcast channels list
 * @param bidsCount              number of bids placed
 * @param bids                   full bid list
 * @param awardedBidId           winning bid id; null until {@link #status} is COMPLETED
 * @param awardedTo              winning partner id; null until award
 * @param createdBy              UUID of TP user who created the tender
 * @param createdAt              creation timestamp
 * @param updatedAt              last-modified timestamp
 */
public record TenderDto(
        String id,
        String tpAccountId,
        String tenderNumber,
        TenderStatus status,
        String title,
        String origin,
        String destination,
        String vehicleType,
        String goodsType,
        LocalDate pickupDate,
        LocalDate deliveryDate,
        Integer refPriceInr,
        String notes,
        List<String> orderIds,
        List<String> broadcastGroupIds,
        Integer broadcastPartnerCount,
        List<String> sentVia,
        Integer bidsCount,
        List<BidDto> bids,
        String awardedBidId,
        String awardedTo,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
