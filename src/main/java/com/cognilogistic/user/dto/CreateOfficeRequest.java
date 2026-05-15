package com.cognilogistic.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/offices} — create a new branch office.
 *
 * <p>Mandatory fields (BR-OFF-01): {@code name}, {@code code}, {@code city}, {@code state}.
 * Missing or blank values for any mandatory field cause Bean Validation to reject the request
 * with 400 {@code VALIDATION_ERROR} before it reaches the service layer.
 *
 * <p>Optional fields: {@code pincode}, {@code address}, {@code gstin}.
 * If {@code gstin} is provided, the service validates it against the standard Indian GSTIN
 * regex and returns 400 {@code INVALID_GSTIN} on failure (BR-OFF-03).
 *
 * <p>{@code code} is normalised to uppercase by the service before persistence (BR-OFF-07)
 * and must be unique within the caller's TP account (BR-OFF-02).
 *
 * @param name     display name of the branch office
 * @param code     short mnemonic code — max 10 characters, normalised to uppercase on save
 * @param city     city of the office location
 * @param state    state of the office location
 * @param pincode  postal/PIN code (optional)
 * @param address  free-text street address (optional)
 * @param gstin    branch-level GSTIN — validated if provided (optional)
 */
public record CreateOfficeRequest(

        @NotBlank(message = "name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "code is required")
        @Size(max = 10)
        String code,

        @NotBlank(message = "city is required")
        @Size(max = 120)
        String city,

        @NotBlank(message = "state is required")
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
        String gstin
) {}
