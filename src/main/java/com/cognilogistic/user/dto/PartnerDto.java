package com.cognilogistic.user.dto;

import java.util.List;

/**
 * Wire DTO for a Logistics Partner (BACKEND_GAPS §6).
 *
 * <p>Returned by {@code GET /api/v1/partners}. Mirrors the front-end's
 * {@code Partner} TypeScript shape:
 *
 * <pre>{@code
 * { "id": "ptn-1", "name": "NorthZone Logistics",
 *   "phone": "9111100001", "vehicleTypes": ["14 ft", "17 ft", "22 ft"],
 *   "region": "Delhi NCR" }
 * }</pre>
 *
 * <p><strong>R5 minimum:</strong> the underlying entity ({@code partner_tp_profiles})
 * carries more fields (GSTIN, languages, address, etc.) but the FE's network-tab
 * only consumes the four below. Additional fields surface as PRs add UI for them.
 *
 * @param id            partner UUID
 * @param name          display name (the company name)
 * @param phone         contact phone number (the owning user's phone)
 * @param vehicleTypes  vehicle types the partner operates; empty list if not specified
 * @param region        free-text region label, derived from {@code service_zone} or
 *                      computed from address; null when neither is set
 */
public record PartnerDto(
        String id,
        String name,
        String phone,
        List<String> vehicleTypes,
        String region
) {}
