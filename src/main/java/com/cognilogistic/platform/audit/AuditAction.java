package com.cognilogistic.platform.audit;

/**
 * Classifies what kind of mutation was performed on the entity referenced by an
 * {@link AuditLog} row.
 *
 * <p>Stored in the {@code audit_logs.action} column as the enum's {@code name()} string.
 * The schema column is VARCHAR(20), so the enum names below must each fit that limit.
 *
 * <p>Mapping guide for the implementer of new audit hooks:
 * <ul>
 *   <li>Use {@link #CREATE} for fresh INSERTs of an entity (e.g. a new order, a new office).</li>
 *   <li>Use {@link #UPDATE} for column-level edits that do not change a state-machine field.</li>
 *   <li>Use {@link #DELETE} for any deletion — including soft-deletes (e.g. flipping {@code is_active}
 *       to {@code false}). The {@code old_value} / {@code new_value} JSON columns make it clear
 *       whether this was a soft or hard delete on read.</li>
 *   <li>Use {@link #STATE_CHANGE} when the change is primarily a status-machine transition
 *       (e.g. {@code orders.status = ACKNOWLEDGED}, {@code tp_accounts.account_status = APPROVED}).
 *       This makes it easy to filter the audit log for "show only state transitions on this entity."</li>
 * </ul>
 *
 * <p>If a single mutation is both a status change and a column edit, prefer {@link #STATE_CHANGE} —
 * the {@code new_value} JSON will still carry every changed field for forensic completeness.
 *
 * <p>See platform.md §4.2 for the full list of tracked entities.
 */
public enum AuditAction {

    /** A new row was inserted (CREATE). */
    CREATE,

    /** Existing columns were updated; no state-machine column changed. */
    UPDATE,

    /** The row was deleted — hard delete, or a soft-delete column flipped. */
    DELETE,

    /** A state-machine column changed (e.g. {@code orders.status}, {@code tp_accounts.account_status}). */
    STATE_CHANGE
}
