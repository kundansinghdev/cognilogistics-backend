package com.cognilogistic.integrationclient.vahan;

/**
 * Response from the VAHAN (MoRTH) vehicle-registry lookup API.
 *
 * <p>Used during fleet confirmation to verify the vehicle's registration and insurance status
 * before accepting an FTL order (BR-09, BR-10).
 *
 * <p>Components:
 * <ul>
 *   <li>{@code vehicleRegistration} — the Indian registration number that was queried</li>
 *   <li>{@code ownerName} — the registered owner's name</li>
 *   <li>{@code makerModel} — manufacturer and model string (e.g., "TATA / Ace")</li>
 *   <li>{@code fuelType} — e.g., "DIESEL", "CNG", "ELECTRIC"</li>
 *   <li>{@code registeredAt} — registration date (YYYY-MM-DD)</li>
 *   <li>{@code fitnessUpto} — fitness certificate validity end date (YYYY-MM-DD)</li>
 *   <li>{@code insuranceUpto} — insurance validity end date (YYYY-MM-DD)</li>
 *   <li>{@code status} — overall registration status (e.g., "ACTIVE", "BLACKLISTED")</li>
 *   <li>{@code warning} — optional warning from the mock or real API (e.g., insurance expiring soon);
 *       {@code null} if no advisory applies</li>
 * </ul>
 */
public record VahanLookupResponse(
        String vehicleRegistration,
        String ownerName,
        String makerModel,
        String fuelType,
        String registeredAt,
        String fitnessUpto,
        String insuranceUpto,
        String status,
        String warning
) {}
