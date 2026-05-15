package com.cognilogistic.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/offices/{id}} — partial update of a branch office.
 *
 * <p>All fields are optional (null = keep existing value). At least one non-null field
 * should be provided; sending an all-null body is a no-op.
 *
 * <p>Business rules enforced by the service:
 * <ul>
 *   <li>If {@code code} is provided, it is normalised to uppercase and checked for uniqueness
 *       within the TP account (BR-OFF-02, BR-OFF-07).</li>
 *   <li>If {@code gstin} is provided, it is validated against the GSTIN regex (BR-OFF-03).</li>
 *   <li>If {@code isActive} is set to {@code false}, the service checks that no non-DELIVERED /
 *       non-CANCELLED orders are assigned to this office. If any exist, 422
 *       {@code OFFICE_HAS_ACTIVE_ORDERS} is returned (BR-OFF-06).</li>
 * </ul>
 *
 * @param name     new display name (optional)
 * @param code     new mnemonic code — normalised to uppercase (optional)
 * @param city     new city (optional)
 * @param state    new state (optional)
 * @param pincode  new postal code (optional)
 * @param address  new street address (optional)
 * @param gstin    new GSTIN — validated if non-null (optional)
 * @param isActive soft-deactivation flag — false triggers active-order check (optional)
 */
public record UpdateOfficeRequest(

        @Size(max = 200)
        String name,

        @Size(max = 10)
        String code,

        @Size(max = 120)
        String city,

        @Size(max = 80)
        String state,

        @Size(max = 10)
        @Pattern(regexp = "^$|^\\d{6}$", message = "pincode must be exactly 6 digits")
        String pincode,

        @Size(max = 500)
        String address,

        @Size(max = 20)
        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "gstin must be a valid 15-character Indian GSTIN")
        String gstin,

        Boolean isActive
) {}
