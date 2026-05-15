package com.cognilogistic.tender.model;

/**
 * Lifecycle status of a {@link Tender}.
 *
 * <p>UAT scaffold: only {@code DRAFT} and {@code IN_PROGRESS} are used in the current
 * implementation. The bid/assignment workflow that drives status through to {@code COMPLETED}
 * is a post-UAT concern.
 */
public enum TenderStatus {
    /** Tender created but not yet open for bidding. */
    DRAFT,
    /** Tender is open and active; fleet providers can submit bids (post-UAT). */
    IN_PROGRESS,
    /** A bid has been accepted and vehicle assigned; terminal state. */
    COMPLETED,
    /** Tender was withdrawn before completion; terminal state. */
    CANCELLED
}
