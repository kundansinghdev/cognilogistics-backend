package com.cognilogistic.integrationclient.gst;

/**
 * SPI for Government GSTIN (GST Identification Number) verification.
 *
 * <p>Provides a standardised way to verify a GSTIN against the GSTN government registry
 * and retrieve the associated business details (legal name, trade name, status).
 * The real implementation is stubbed for UAT; it will integrate with the GSTN sandbox
 * or a third-party API aggregator (e.g., Quicko, Masters India) post-UAT.
 *
 * <p>Activated by the {@code CompanyService} GSTIN-lookup endpoint only; not called on
 * the critical order-creation path.
 */
public interface GstClient {

    /**
     * Looks up a GSTIN and returns the associated GST registration details.
     *
     * @param gstin the 15-character GSTIN to verify (format: state-code + PAN + entity-code)
     * @return the lookup result containing legal name, trade name, and registration status
     */
    GstLookupResponse lookup(String gstin);
}
