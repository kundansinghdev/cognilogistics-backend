package com.cognilogistic.legal.dto;

import com.cognilogistic.legal.model.LegalDocVersion;

import java.time.Instant;
import java.util.Map;

/**
 * Wire shape for {@code GET /api/v1/legal/current-versions}.
 *
 * <p>Front-end reads this on the signup-screen mount so it knows which version
 * string to send back on {@code POST /auth/setup-pin}. Spec §4.2.
 *
 * @param terms   current Terms of Service version + when it was published
 * @param privacy current Privacy Policy version + when it was published
 */
public record LegalVersionsDto(VersionEntry terms, VersionEntry privacy) {

    /**
     * One row of the response.
     *
     * @param version     ISO date string (e.g. {@code "2026-05-08"})
     * @param publishedAt server timestamp when this version became current
     */
    public record VersionEntry(String version, Instant publishedAt) {
        public static VersionEntry from(LegalDocVersion entity) {
            return new VersionEntry(entity.getVersion(), entity.getPublishedAt());
        }
    }

    /**
     * Builds the wire DTO from the two entity rows. Either argument may be
     * {@code null} (defensive — if a doc type is missing from the seed,
     * returns a placeholder rather than NPE'ing the caller).
     *
     * @param byType map of {@link com.cognilogistic.legal.model.DocType} to entity
     * @return assembled DTO
     */
    public static LegalVersionsDto from(Map<com.cognilogistic.legal.model.DocType, LegalDocVersion> byType) {
        LegalDocVersion termsEntity = byType.get(com.cognilogistic.legal.model.DocType.TERMS);
        LegalDocVersion privacyEntity = byType.get(com.cognilogistic.legal.model.DocType.PRIVACY);
        return new LegalVersionsDto(
                termsEntity == null ? null : VersionEntry.from(termsEntity),
                privacyEntity == null ? null : VersionEntry.from(privacyEntity));
    }
}
