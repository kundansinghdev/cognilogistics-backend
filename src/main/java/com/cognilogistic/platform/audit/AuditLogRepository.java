package com.cognilogistic.platform.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AuditLog}.
 *
 * <p>Read access is the dominant use case — support / compliance / Admin Portal queries
 * "what happened to entity X?" or "what did admin Y do during impersonation?" Writes are
 * append-only, performed by {@link AuditService#append}.
 *
 * <p>All finders here intentionally return {@code List} or {@code Page} rather than
 * {@code Stream} so callers can inspect the full result set in their own transaction
 * without worrying about session lifetime.
 *
 * <p>The repository does NOT scope results by {@code tp_account_id} because audit rows
 * have no such column — multi-tenant filtering must be done in the service layer by
 * looking up the entity's own {@code tp_account_id} (e.g., for tracked orders, join via
 * {@code orders.tp_account_id} and apply caller-side scoping). The Admin Portal sees
 * everything; tenant-scoped views are derived in {@code AdminAuditService} (post-UAT).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Returns the chronological audit trail for a specific entity instance.
     *
     * <p>Used by the Admin Portal "history" tab on any tracked entity, and by support
     * tooling. Results are sorted oldest → newest so the trail reads naturally; reverse
     * on the controller layer if the UI needs newest-first.
     *
     * @param entityType the database table name (e.g. {@code "orders"} — see {@link AuditLog#getEntityType()})
     * @param entityId   the row id whose history is being requested
     * @return chronological list, oldest first; empty if no audit rows exist yet
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, String entityId);

    /**
     * Paginated audit history for a single entity. Use this when an entity has lots of
     * tracked changes (e.g., a busy order with dozens of state transitions) and the UI
     * paginates the timeline.
     *
     * @param entityType the database table name
     * @param entityId   the row id
     * @param pageable   page size / number / sort direction
     * @return a page of audit rows
     */
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    /**
     * Returns audit rows performed by a specific actor (the user whose tokens were used
     * to call the mutating endpoint). For impersonation sessions this returns the rows
     * recorded against the <em>impersonated</em> tenant — not the admin. For "what did
     * the admin do?" use {@link #findByImpersonatedByUserId}.
     *
     * @param actorUserId the {@code users.id} value of the actor
     * @param pageable    pagination
     * @return page of audit rows authored (in the SecurityContext sense) by that user
     */
    Page<AuditLog> findByActorUserId(String actorUserId, Pageable pageable);

    /**
     * Returns every audit row that was written while the given Platform Admin was
     * impersonating a tenant. Powers the Admin Portal's per-admin forensics tab.
     *
     * @param impersonatedByUserId the {@code users.id} of the {@code COGNILOGISTIC_ADMIN}
     * @param pageable             pagination
     * @return page of audit rows tagged with that admin's impersonation session
     */
    Page<AuditLog> findByImpersonatedByUserId(String impersonatedByUserId, Pageable pageable);

    /**
     * Range query for retention / archival jobs. Returns rows older than the given cut-off
     * (typically {@code now() - 7 years} per DPDP). The caller is expected to stream-process
     * and then issue a bulk delete; for safety this method does NOT delete on its own.
     *
     * @param threshold rows with {@code created_at < threshold} will be returned
     * @param pageable  pagination — keep page size modest (e.g. 1000) to avoid heap pressure
     * @return page of old audit rows ready for archival
     */
    Page<AuditLog> findByCreatedAtLessThan(Instant threshold, Pageable pageable);

    /**
     * Counts the audit rows for a single entity — used by the Admin Portal entity-detail
     * widget that shows "X changes recorded" without loading the full list.
     *
     * @param entityType the database table name
     * @param entityId   the row id
     * @return number of audit rows for that entity (0 if untracked)
     */
    long countByEntityTypeAndEntityId(String entityType, String entityId);

    /**
     * Bulk-delete archived rows after they've been copied to cold storage. Used by the
     * retention job (post-UAT). Marked {@code @Modifying} so JPA executes a DELETE instead
     * of selecting + cascading entity removal.
     *
     * @param threshold rows with {@code created_at < threshold} are deleted
     * @return number of rows removed
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :threshold")
    int deleteByCreatedAtLessThan(@Param("threshold") Instant threshold);
}
