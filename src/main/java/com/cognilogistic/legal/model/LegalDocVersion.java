package com.cognilogistic.legal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity for the {@code legal_doc_versions} table — tiny lookup of the
 * currently-published version per legal doc type.
 *
 * <p>Front-end reads this on the signup-screen mount (see
 * {@code GET /api/v1/legal/current-versions}) so it knows which version
 * string to send back on {@code POST /auth/setup-pin}. The same row drives
 * server-side validation: the submitted {@code acceptedTermsVersion} /
 * {@code acceptedPrivacyVersion} must equal what's here, otherwise the
 * service raises {@code CONSENT_VERSION_MISMATCH}.
 *
 * <p>The doc body itself is NOT stored here — the front-end serves the HTML
 * at {@code /terms} and {@code /privacy}. We only persist the version string.
 *
 * <p><strong>Schema reference:</strong> {@code V20260508005__user_consents_and_legal_doc_versions.sql}.
 */
@Entity
@Table(name = "legal_doc_versions")
@Getter
@Setter
@NoArgsConstructor
public class LegalDocVersion {

    /**
     * The doc type. Acts as the PK — there's only ever one current version
     * per doc type. Stored as the enum's {@code name()} string.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private DocType docType;

    /** Currently published version string (typically an ISO date, e.g. "2026-05-08"). */
    @Column(name = "version", nullable = false, length = 32)
    private String version;

    /**
     * When this version became current. Set in service code on update so the
     * application clock owns the timestamp (not MySQL's). DB has a
     * {@code DEFAULT CURRENT_TIMESTAMP} as a safety net for any rows
     * inserted via raw SQL.
     */
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
