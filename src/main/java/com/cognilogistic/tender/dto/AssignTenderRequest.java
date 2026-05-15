package com.cognilogistic.tender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/partner/tenders/{id}/assign}
 * (BACKEND_GAPS §7b).
 *
 * <p>Submitted by the winning partner after a tender is awarded. Locks once
 * written — the service returns {@code INVALID_TRANSITION} on a second attempt.
 *
 * @param vehicleNumber Indian vehicle registration; uppercased server-side
 * @param driverName    driver's full name — required
 * @param driverDl      driver licence number; uppercased server-side. Optional.
 */
public record AssignTenderRequest(

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2}[0-9]{1,2}[A-Za-z]{1,3}[0-9]{1,4}",
                message = "Invalid Indian vehicle registration")
        String vehicleNumber,

        @NotBlank
        String driverName,

        String driverDl
) {}
