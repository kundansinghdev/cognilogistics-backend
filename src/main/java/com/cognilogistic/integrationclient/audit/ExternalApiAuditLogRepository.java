package com.cognilogistic.integrationclient.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ExternalApiAuditLog}.
 *
 * <p>Read access is the dominant use case — ops dashboards, support queries,
 * cost reconciliation. Writes are append-only from
 * {@link com.cognilogistic.integrationclient.vahan.VahanService} (and the
 * Sarathi/GST analogues post-UAT).
 *
 * <p>The retention story is the same as {@code audit_logs} — DPDP 7-year window,
 * archival job sweeps old rows. Not implemented yet (UAT scale doesn't need it);
 * see platform.md §4.4.
 *
 * <p><strong>2026-05-08:</strong> repository methods aligned with the v5.0
 * entity shape after the {@code external_api_audit_log} table was simplified.
 * Earlier draft methods referenced removed fields ({@code integration_name} /
 * {@code related_entity_*}) and broke Spring Data query derivation at boot.
 */
public interface ExternalApiAuditLogRepository extends JpaRepository<ExternalApiAuditLog, String> {

    /**
     * Paginated audit history filtered by integration service. Used by the Admin
     * Portal's "External API calls" tab.
     *
     * @param service  one of {@code "VAHAN"} / {@code "SARATHI"} / {@code "GST"}
     * @param pageable pagination + sort
     * @return page of audit rows
     */
    Page<ExternalApiAuditLog> findByService(String service, Pageable pageable);

    /**
     * Audit history for a specific request reference (typically an order id for
     * VAHAN/SARATHI calls, or a tp_account_id for GST calls). Lets ops trace
     * every external call that was made while processing this entity.
     *
     * @param service    one of {@code "VAHAN"} / {@code "SARATHI"} / {@code "GST"}
     * @param requestRef the request reference (CHAR(36) UUID — semantics depend on service)
     * @param pageable   pagination + sort
     * @return page of audit rows tagged with that service + reference
     */
    Page<ExternalApiAuditLog> findByServiceAndRequestRef(
            String service, String requestRef, Pageable pageable);
}
