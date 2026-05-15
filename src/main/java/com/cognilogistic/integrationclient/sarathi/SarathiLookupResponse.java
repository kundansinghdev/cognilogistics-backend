package com.cognilogistic.integrationclient.sarathi;

import java.util.List;

/**
 * Response from the Sarathi (MoRTH) driving-licence verification API.
 *
 * <p>Aligned with the front-end's expected wire shape (BACKEND_GAPS §12.2).
 * Mirrors the {@code VahanLookupResponse} pattern: a flat record with the
 * advisory {@code warning} field for "expiring soon" / "renew before XYZ"
 * notices the FE renders as a chip.
 *
 * @param driverDl       the licence number that was looked up (echoes the request)
 * @param driverName     name as registered with the issuing RTO
 * @param fatherName     father's name (for identity verification)
 * @param dateOfBirth    {@code YYYY-MM-DD}
 * @param vehicleClasses licence categories — e.g. {@code ["LMV", "HMV"]}
 * @param status         {@code "ACTIVE"} / {@code "EXPIRED"} / {@code "SUSPENDED"} / etc.
 * @param validFrom      issue date {@code YYYY-MM-DD}
 * @param validUpto      validity end date {@code YYYY-MM-DD}
 * @param warning        optional advisory text (e.g. "Expiring within 60 days"); null when not applicable
 */
public record SarathiLookupResponse(
        String driverDl,
        String driverName,
        String fatherName,
        String dateOfBirth,
        List<String> vehicleClasses,
        String status,
        String validFrom,
        String validUpto,
        String warning) {}
