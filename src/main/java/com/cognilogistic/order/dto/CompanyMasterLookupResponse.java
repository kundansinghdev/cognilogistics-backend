package com.cognilogistic.order.dto;

/**
 * Response for {@code GET /api/v1/companies/master-lookup} — resolves a GSTIN against
 * the TP's Company Master (not the external GST SPI).
 *
 * @param companyId            primary key of the matched company row
 * @param gstin                GSTIN echoed from the request (uppercase)
 * @param legalName            company legal name for auto-fill
 * @param primaryContactPhone  master contact phone for WhatsApp auto-fill; may be null
 */
public record CompanyMasterLookupResponse(
        String companyId,
        String gstin,
        String legalName,
        String primaryContactPhone
) {}
