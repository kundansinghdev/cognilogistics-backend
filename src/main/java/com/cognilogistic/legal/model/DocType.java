package com.cognilogistic.legal.model;

/**
 * Type of legal document a user can consent to.
 *
 * <p>Stored as the enum's {@code name()} string in {@code legal_doc_versions.doc_type}
 * and {@code user_consents.doc_type} — both VARCHAR(16) free-text columns. The
 * column type is intentionally not a DB-level ENUM so adding new doc types
 * (e.g. {@code MARKETING_OPT_IN}, {@code COOKIE_POLICY}) doesn't require a
 * migration.
 *
 * <p>Per BACKEND_SPEC_TC_CONSENT.md §2: only TERMS + PRIVACY are in scope for v1.
 */
public enum DocType {

    /** Terms of Service. */
    TERMS,

    /** Privacy Policy. */
    PRIVACY
}
