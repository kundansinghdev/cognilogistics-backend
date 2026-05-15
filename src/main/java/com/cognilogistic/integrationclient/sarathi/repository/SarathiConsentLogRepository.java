package com.cognilogistic.integrationclient.sarathi.repository;

import com.cognilogistic.integrationclient.sarathi.model.SarathiConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SarathiConsentLog}.
 *
 * <p>The dominant access pattern is "did this DL number have consent recorded
 * recently?" — we look up the most recent row before issuing a Sarathi API call.
 */
public interface SarathiConsentLogRepository extends JpaRepository<SarathiConsentLog, String> {

    /**
     * Returns the most recent consent row for a (DL number, user) pair.
     *
     * <p>"Most recent" because consent is time-bound — DPDP guidance says we shouldn't
     * lean on a months-old click. The application enforces a freshness window
     * (typically 24 hours) on top of the row's existence.
     *
     * @param dlNumber the driving-licence number being looked up
     * @param userId   the user issuing the lookup
     * @return the most recent matching row, if any
     */
    Optional<SarathiConsentLog> findFirstByDlNumberAndUserIdOrderByConsentAtDesc(
            String dlNumber, String userId);

    /**
     * Returns whether at least one consent row exists for the given (orderId, DL) pair.
     *
     * <p>Used by the BR-FLT-04 consent gate on {@code SarathiService.lookup} — that path
     * previously used {@code findAll().stream().anyMatch(...)}, which is a full-table scan
     * that scales linearly with consent-log volume. This derived query uses the
     * {@code (order_id, dl_number)} composite index instead.
     *
     * @param orderId  the order context
     * @param dlNumber the driving-licence number (uppercase, matches the column convention)
     * @return {@code true} when at least one matching row exists
     */
    boolean existsByOrderIdAndDlNumber(String orderId, String dlNumber);
}
