package com.cognilogistic.reports.service;

import com.cognilogistic.tender.model.Tender;
import com.cognilogistic.tender.model.TenderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Aggregations over the {@code tenders} table — tender conversion metrics.
 *
 * <p>"Conversion rate" = {@code COMPLETED / total tenders created} in the date range.
 * Cancellation count is reported alongside so the client can see how the funnel breaks
 * down (high creation but high cancellation indicates a different problem than low
 * creation outright).
 */
@Service
public class TenderMetricsService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Counts tenders by terminal-status bucket for a TP in a date range.
     *
     * <p>The result row is {@code [total, completed, cancelled]}; the controller
     * passes the three values to {@link com.cognilogistic.reports.dto.TenderConversionDto#compute}
     * which computes the conversion rate.
     *
     * @param tpAccountId TP filter (required)
     * @param from        inclusive lower bound on {@code tenders.created_at}; may be {@code null}
     * @param to          exclusive upper bound; may be {@code null}
     * @return three-element {@code Object[]}: {@code [Long total, Long completed, Long cancelled]}
     */
    @Transactional(readOnly = true)
    public Object[] funnelCounts(String tpAccountId, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("""
                SELECT
                    COUNT(t.id),
                    SUM(CASE WHEN t.status = :completed THEN 1 ELSE 0 END),
                    SUM(CASE WHEN t.status = :cancelled THEN 1 ELSE 0 END)
                  FROM Tender t
                 WHERE t.tpAccountId = :tp
                   AND (:from IS NULL OR t.createdAt >= :from)
                   AND (:to   IS NULL OR t.createdAt <  :to)
                """, Object[].class);
        q.setParameter("tp", tpAccountId);
        q.setParameter("completed", TenderStatus.COMPLETED);
        q.setParameter("cancelled", TenderStatus.CANCELLED);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return q.getSingleResult();
    }

    /** Reference to the entity class so the JPQL type literal {@code Tender} resolves at compile time. */
    @SuppressWarnings("unused")
    private static final Class<Tender> TENDER_ENTITY = Tender.class;
}
