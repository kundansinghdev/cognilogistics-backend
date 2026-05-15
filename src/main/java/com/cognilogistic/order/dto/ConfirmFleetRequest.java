package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/orders/{id}/confirm-fleet}.
 *
 * <p>Aligned with the front-end's FleetConfirmModal payload (BACKEND_GAPS §2.4).
 * Canonical names: {@code vehicleNumber}, {@code assignedVehicle}, {@code driverPhone}.
 * Legacy backend names ({@code vehicleRegistration}, {@code vehicleType}) are still
 * accepted so the front-end can switch over field-by-field.
 *
 * <p><strong>Validation rules:</strong>
 * <ul>
 *   <li>{@code driverName} — required for both FTL and PTL (BR-FLT-02).</li>
 *   <li>{@code vehicleNumber} — required for FTL (BR-ORD-09); auto-uppercased server-side.</li>
 *   <li>{@code driverDl} — optional; auto-uppercased server-side when present.</li>
 *   <li>{@code vahanConsentId} — required for FTL when the FTL Vahan-consent gate
 *       is on. Behind a property ({@code orders.fleet.require-vahan-consent},
 *       default {@code true}); R1 lets ops relax it for UAT demos by setting it
 *       to {@code false}.</li>
 * </ul>
 *
 * @param vehicleRegistration legacy alias for {@link #vehicleNumber}
 * @param vehicleNumber       canonical vehicle registration (Indian format), required for FTL
 * @param vehicleId           internal fleet vehicle FK (optional)
 * @param driverName          driver name — required (FTL + PTL)
 * @param driverPhone         driver contact phone (optional)
 * @param driverDl            driver licence number (optional, advisory)
 * @param vahanConsentId      FTL Vahan consent log row id (gated by property)
 * @param sarathiConsentId    FTL Sarathi consent log row id (advisory)
 * @param vehicleType         legacy enum form of {@link #assignedVehicle}
 * @param assignedVehicle     canonical vehicle body-type string (matches {@link VehicleType} display name)
 * @param vahanStatus         outcome of the Vahan check ({@code VERIFIED} / {@code WARNING} / etc.)
 * @param sarathiStatus       outcome of the Sarathi check (same value set)
 */
public record ConfirmFleetRequest(

        @Size(max = 20, message = "vehicleRegistration must be at most 20 characters")
        @Pattern(regexp = "^$|^[A-Za-z]{2}[0-9]{1,2}[A-Za-z]{1,3}[0-9]{1,4}$",
                message = "Invalid Indian vehicle registration")
        String vehicleRegistration,

        @Size(max = 20, message = "vehicleNumber must be at most 20 characters")
        @Pattern(regexp = "^$|^[A-Za-z]{2}[0-9]{1,2}[A-Za-z]{1,3}[0-9]{1,4}$",
                message = "Invalid Indian vehicle registration")
        String vehicleNumber,

        @Size(max = 36, message = "vehicleId must be at most 36 characters")
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "vehicleId must be a UUID")
        String vehicleId,

        @NotBlank(message = "driverName is required")
        @Size(max = 255, message = "driverName must be at most 255 characters")
        String driverName,

        @Size(max = 20, message = "driverPhone must be at most 20 characters")
        @Pattern(regexp = "^$|^\\+?\\d{10,15}$", message = "phone must be 10-15 digits")
        String driverPhone,
        @Size(max = 30, message = "driverDl must be at most 30 characters")
        String driverDl,

        @Size(max = 36, message = "vahanConsentId must be at most 36 characters")
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "vahanConsentId must be a UUID")
        String vahanConsentId,
        @Size(max = 36, message = "sarathiConsentId must be at most 36 characters")
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "sarathiConsentId must be a UUID")
        String sarathiConsentId,

        VehicleType vehicleType,
        @Size(max = 50, message = "assignedVehicle must be at most 50 characters")
        String assignedVehicle,

        @Size(max = 20, message = "vahanStatus must be at most 20 characters")
        String vahanStatus,
        @Size(max = 20, message = "sarathiStatus must be at most 20 characters")
        String sarathiStatus
) {

    /**
     * Returns the canonical vehicle registration value, normalising to uppercase.
     * Falls back to the legacy {@code vehicleRegistration} field. Returns {@code null}
     * when neither is supplied.
     */
    public String effectiveVehicleNumber() {
        String v = vehicleNumber != null ? vehicleNumber : vehicleRegistration;
        return v == null ? null : v.toUpperCase();
    }

    /**
     * Resolves the {@link VehicleType}. Order of precedence:
     * <ol>
     *   <li>Explicit {@link #vehicleType} enum.</li>
     *   <li>{@link #assignedVehicle} string parsed against the {@link VehicleType} display names.</li>
     * </ol>
     * Returns {@code null} when neither is supplied or when {@code assignedVehicle}
     * doesn't match any known type — caller treats null as "leave existing value alone."
     */
    public VehicleType effectiveVehicleType() {
        if (vehicleType != null) return vehicleType;
        if (assignedVehicle == null) return null;
        for (VehicleType vt : VehicleType.values()) {
            if (vt.getDisplayName().equalsIgnoreCase(assignedVehicle)) return vt;
        }
        return null;
    }
}
