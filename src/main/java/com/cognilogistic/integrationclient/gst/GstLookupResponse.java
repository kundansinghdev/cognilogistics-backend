package com.cognilogistic.integrationclient.gst;

/**
 * Response from the GST GSTIN-verification API.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code gstin} — the GSTIN that was looked up (echo of the request)</li>
 *   <li>{@code legalName} — the legal entity name registered with GST</li>
 *   <li>{@code tradeName} — the trade/brand name (may differ from legal name)</li>
 *   <li>{@code status} — GST registration status (e.g., "ACTIVE", "CANCELLED", "SUSPENDED")</li>
 * </ul>
 */
public record GstLookupResponse(String gstin, String legalName, String tradeName, String status) {}
