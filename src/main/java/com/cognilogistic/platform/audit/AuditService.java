package com.cognilogistic.platform.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Convenience service that writes a row into the {@code audit_logs} table.
 *
 * <p>This is the single entry point service code should use to record an audit event;
 * the repository's {@code save()} method is intentionally not called from elsewhere
 * because the audit row's id, timestamp, and column-shape rules belong here in one place.
 *
 * <p>Why not an aspect right now? The cross-cutting {@code @Auditable} aspect is post-UAT
 * (see platform.md §13 PR P3). Until then, services that perform a tracked mutation call
 * {@link #append} explicitly. The aspect can later replace those calls without changing
 * this service's contract.
 *
 * <p><strong>Transactional behaviour:</strong> {@link #append} runs in
 * {@link Propagation#REQUIRES_NEW} so the audit insert is committed even if the calling
 * transaction rolls back. Reasoning: an audit row is the proof that an action was
 * <em>attempted</em>, which is often what compliance needs even when the action itself
 * failed. If you specifically want the audit to roll back with the caller (e.g., success-only
 * recording), call {@link #appendInCallerTx} instead — but think carefully before doing so.
 *
 * <p><strong>Impersonation context:</strong> the {@code impersonatedByUserId} parameter
 * is normally pulled from the authenticated principal's JWT claims by the calling code.
 * The post-UAT aspect will read it from {@code SecurityContextHolder} automatically; for
 * now, callers who need to record an impersonation context must pass it through.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit event in its own transaction, surviving any rollback in the caller.
     *
     * <p>This is the right method to call from a service that has just performed (or
     * attempted) a tracked mutation. Generates the row's UUID and {@code createdAt}
     * timestamp internally; the caller never assigns these.
     *
     * @param entityType            the database table name (e.g. {@code "orders"})
     * @param entityId              the affected row's id (CHAR(36) UUID string)
     * @param action                what kind of mutation was performed
     * @param actorUserId           the user id of the actor, or {@code null} for system actions
     * @param actorName             the actor's display name at the time of the action; {@code null} for system
     * @param impersonatedByUserId  for admin impersonation: the {@code COGNILOGISTIC_ADMIN}'s user id;
     *                              {@code null} for non-impersonation activity
     * @param oldValue              JSON snapshot of the row pre-mutation, or {@code null} for CREATE
     * @param newValue              JSON snapshot of the changed fields post-mutation, or {@code null} for DELETE
     * @param ipAddress             client IP from the originating request, or {@code null} for system
     * @return the persisted audit row (with id and createdAt populated)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog append(String entityType,
                           String entityId,
                           AuditAction action,
                           String actorUserId,
                           String actorName,
                           String impersonatedByUserId,
                           String oldValue,
                           String newValue,
                           String ipAddress) {

        AuditLog row = new AuditLog();
        // SCHEMA: id is CHAR(36) — see AuditLog#id Javadoc for why we use String not UUID.
        row.setId(UUID.randomUUID().toString());
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setAction(action);
        row.setActorUserId(actorUserId);
        row.setActorName(actorName);
        row.setImpersonatedByUserId(impersonatedByUserId);
        row.setOldValue(oldValue);
        row.setNewValue(newValue);
        row.setIpAddress(ipAddress);
        // We set createdAt in code (not via the DB default) so unit tests that use a fixed Clock
        // can produce deterministic timestamps without faking MySQL's NOW().
        row.setCreatedAt(Instant.now());

        return repository.save(row);
    }

    /**
     * Records an audit event in the caller's existing transaction (no new tx is opened).
     *
     * <p>Use this only when you specifically want the audit row to be rolled back if the
     * caller's transaction rolls back — for example, when the audit is purely a "successful
     * action proof" and a rolled-back action shouldn't leave a misleading audit record.
     *
     * <p>For most cases prefer {@link #append}. When in doubt, use {@link #append}.
     *
     * @see #append for parameter documentation
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog appendInCallerTx(String entityType,
                                     String entityId,
                                     AuditAction action,
                                     String actorUserId,
                                     String actorName,
                                     String impersonatedByUserId,
                                     String oldValue,
                                     String newValue,
                                     String ipAddress) {

        AuditLog row = new AuditLog();
        row.setId(UUID.randomUUID().toString());
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setAction(action);
        row.setActorUserId(actorUserId);
        row.setActorName(actorName);
        row.setImpersonatedByUserId(impersonatedByUserId);
        row.setOldValue(oldValue);
        row.setNewValue(newValue);
        row.setIpAddress(ipAddress);
        row.setCreatedAt(Instant.now());

        return repository.save(row);
    }
}
