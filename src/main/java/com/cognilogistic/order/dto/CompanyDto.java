package com.cognilogistic.order.dto;

/**
 * Read-model DTO for company master-data API responses.
 *
 * <p>Aligned with the front-end's {@code Company} type (BACKEND_GAPS §3.2).
 *
 * <p><strong>Wire compatibility (rolling cutover).</strong> Two field-name pairs
 * carry identical values so the FE can switch over field-by-field. The legacy
 * names are dropped in a follow-up:
 * <ul>
 *   <li>{@code name} (legacy) ↔ {@code legalName} (canonical, matches schema column)</li>
 *   <li>{@code contactName} / {@code contactPhone} / {@code contactEmail} (legacy)
 *       ↔ {@code primaryContactName} / {@code primaryContactPhone} / {@code primaryContactEmail}
 *       (canonical, matches schema columns)</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code companies} (schema.sql v5.0 lines 303–329).
 *
 * @param id                   database primary key
 * @param tpAccountId          owning TP account (companies are not shared across tenants)
 * @param legalName            registered legal name — canonical
 * @param name                 legacy alias for {@link #legalName}
 * @param tradeName            optional brand / trade name
 * @param gstin                15-char Indian GSTIN; null when {@link #noGst} is true
 * @param noGst                true when the company is not GST-registered
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
 * @param isActive             soft-delete flag — false hides the row from GSTIN-lookup dropdowns
 * @param linkedOrderCount     orders in this TP with {@code company_id} pointing at this company (any status)
 */
public record CompanyDto(
        String id,
        String tpAccountId,
        String legalName,
        String name,
        String tradeName,
        String gstin,
        boolean noGst,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String primaryContactName,
        String contactName,
        String primaryContactPhone,
        String contactPhone,
        String primaryContactEmail,
        String contactEmail,
        String notes,
        boolean isActive,
        long linkedOrderCount
) {}
