package com.cognilogistic.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/companies} and {@code PATCH /api/v1/companies/{id}}.
 *
 * <p>Aligned with the front-end's company form (BACKEND_GAPS §3.2). Carries both
 * the canonical schema-column names ({@code legalName}, {@code primaryContact*})
 * and the legacy aliases ({@code name}, {@code contactName}, …) so the FE can
 * switch over field-by-field.
 *
 * <p>{@code legalName} or {@code name} is required at create time — at least one
 * of the two must be present. PATCH allows both to be null (no-op for that field).
 * The service uses {@link #effectiveLegalName} to coalesce; create-time validation
 * lives in {@code CompanyService} (it throws {@code VALIDATION_ERROR} when both
 * are absent).
 *
 * <p>{@code noGst} is permissive: when true, {@code gstin} is allowed to be null.
 * When false (or null), {@code gstin} is validated against the Indian 15-char
 * regex by the service layer.
 *
 * <p>Uniqueness within a transport-provider account is <strong>not</strong> expressible
 * as a single-field bean constraint: {@code CompanyService} enforces no duplicate
 * GSTIN (GST-registered rows) and no duplicate legal name among no-GST rows
 * (case-insensitive), with a matching DB unique index on {@code (tp_account_id, gstin)}.
 *
 * @param legalName            registered legal name — canonical
 * @param name                 legacy alias for {@link #legalName}
 * @param tradeName            brand / trade name (optional)
 * @param gstin                15-char Indian GSTIN; null when {@link #noGst} is true
 * @param noGst                true → company is not GST-registered ({@code gstin} ignored)
 * @param addressLine1         street address line 1
 * @param addressLine2         street address line 2
 * @param city                 city
 * @param state                state
 * @param pincode              postal code
 * @param primaryContactName   primary contact name — canonical
 * @param contactName          legacy alias for {@link #primaryContactName}
 * @param primaryContactPhone  primary contact phone — canonical
 * @param contactPhone         legacy alias for {@link #primaryContactPhone}
 * @param primaryContactEmail  primary contact email — canonical
 * @param contactEmail         legacy alias for {@link #primaryContactEmail}
 * @param notes                free-text internal notes
 * @param isActive             soft-delete flag (typically only sent on PATCH)
 */
public record CreateCompanyRequest(

        @Size(max = 255) String legalName,
        @Size(max = 255) String name,

        @Size(max = 255) String tradeName,

        @Size(max = 15)
        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "gstin must be a valid 15-character Indian GSTIN")
        String gstin,
        Boolean noGst,

        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 10)
        @Pattern(regexp = "^$|^\\d{6}$", message = "pincode must be exactly 6 digits")
        String pincode,

        @Size(max = 255) String primaryContactName,
        @Size(max = 255) String contactName,

        @Size(max = 15)
        @Pattern(regexp = "^$|^\\+?[0-9]{10,15}$", message = "contact phone must be 10–15 digits, optional leading +")
        String primaryContactPhone,
        @Size(max = 15)
        @Pattern(regexp = "^$|^\\+?[0-9]{10,15}$", message = "contact phone must be 10–15 digits, optional leading +")
        String contactPhone,

        @Size(max = 255)
        @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "contact email must be a valid address")
        String primaryContactEmail,
        @Size(max = 255)
        @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "contact email must be a valid address")
        String contactEmail,

        @Size(max = 500) String notes,
        Boolean isActive

) {

    /** Canonical legal name, falling back to legacy {@code name}. */
    public String effectiveLegalName() {
        return legalName != null ? legalName : name;
    }

    /** Canonical primary contact name, falling back to legacy {@code contactName}. */
    public String effectiveContactName() {
        return primaryContactName != null ? primaryContactName : contactName;
    }

    /** Canonical primary contact phone, falling back to legacy {@code contactPhone}. */
    public String effectiveContactPhone() {
        return primaryContactPhone != null ? primaryContactPhone : contactPhone;
    }

    /** Canonical primary contact email, falling back to legacy {@code contactEmail}. */
    public String effectiveContactEmail() {
        return primaryContactEmail != null ? primaryContactEmail : contactEmail;
    }

    /** Effective {@code noGst} value, defaulting to {@code false} when null. */
    public boolean effectiveNoGst() {
        return noGst != null && noGst;
    }
}
