package com.cognilogistic.legal.dto;

import com.cognilogistic.legal.model.DocType;
import com.cognilogistic.legal.model.UserConsent;

import java.time.Instant;

/**
 * Wire shape for one row of {@code GET /api/v1/admin/users/{id}/consents}.
 *
 * <p>Exposes the {@code user_consents} columns verbatim — admin-only endpoint,
 * so the IP / User-Agent fields are intentionally NOT redacted here. The audit
 * use case is "who agreed to what, from where, when?" and that needs the
 * raw values.
 *
 * @param docType    TERMS or PRIVACY
 * @param docVersion accepted version string
 * @param acceptedAt server timestamp at consent
 * @param ipAddress  client IP; nullable
 * @param userAgent  raw User-Agent header; nullable
 */
public record UserConsentDto(
        DocType docType,
        String docVersion,
        Instant acceptedAt,
        String ipAddress,
        String userAgent) {

    /** Maps the entity to its wire form. */
    public static UserConsentDto from(UserConsent entity) {
        return new UserConsentDto(
                entity.getDocType(),
                entity.getDocVersion(),
                entity.getAcceptedAt(),
                entity.getIpAddress(),
                entity.getUserAgent());
    }
}
