package com.cognilogistic.reports.service;

import com.cognilogistic.order.model.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Aggregations over the {@code orders} table.
 *
 * <p>Uses {@link EntityManager} directly with JPQL rather than extending
 * {@link com.cognilogistic.order.repository.OrderRepository}. Keeping cross-module
 * surface narrow: reports reads orders, but doesn't co-own the order repository's
 * interface.
 *
 * <p>All queries are TP-scoped and date-range bounded — multi-tenancy + index
 * coverage. The optional {@code officeId} parameter narrows further; passing
 * {@code null} for any optional means "no filter on that field".
 */
@Service
public class OrderMetricsService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Returns {@code (status, count)} tuples for orders created in the date range.
     *
     * <p>Result order is undefined; the DTO mapper
     * ({@link com.cognilogistic.reports.dto.OrderStatusCountsDto#from(String, String, List)})
     * pre-fills every {@link com.cognilogistic.order.model.OrderStatus} value to zero
     * before overlaying the rows, so missing statuses don't show up as gaps in the response.
     *
     * @param tpAccountId tenancy filter (always required)
     * @param from        inclusive lower bound on {@code orders.created_at}; may be {@code null}
     * @param to          exclusive upper bound on {@code orders.created_at}; may be {@code null}
     * @param officeId    optional office filter; may be {@code null} for TP-wide
     * @return list of two-element {@code Object[]}: {@code [OrderStatus, Long]}
     */
    @Transactional(readOnly = true)
    public List<Object[]> countByStatus(String tpAccountId, Instant from, Instant to, String officeId) {
        // JPQL — :param IS NULL OR <expr> is the standard Spring-Data pattern for
        // optional filters (matches OrderRepository.search). The DB optimiser short-circuits
        // when the bind value is null, so there's no per-row cost from the OR.
        TypedQuery<Object[]> q = em.createQuery("""
                SELECT o.status, COUNT(o.id)
                  FROM Order o
                 WHERE o.tpAccountId = :tp
                   AND (:from IS NULL OR o.createdAt >= :from)
                   AND (:to   IS NULL OR o.createdAt <  :to)
                   AND (:officeId IS NULL OR o.assignedOfficeId = :officeId)
                 GROUP BY o.status
                """, Object[].class);
        q.setParameter("tp", tpAccountId);
        q.setParameter("from", from);
        q.setParameter("to", to);
        q.setParameter("officeId", officeId);
        return q.getResultList();
    }

    /**
     * Average time-from-creation-to-delivery for orders that reached DELIVERED in the
     * date range, in seconds. Returns {@code null} if no qualifying orders exist —
     * the DTO surfaces that as "no data" to the client.
     *
     * <p><strong>UAT shortcut:</strong> we measure {@code orders.updated_at} when the
     * status reached DELIVERED rather than digging into {@code order_status_log}. The
     * status-log table has the precise transition timestamp, but this approximation
     * is good enough for the demo report. Post-UAT R3 swaps to the log query.
     *
     * @return average seconds from creation to delivery, or {@code null} if zero rows
     */
    @Transactional(readOnly = true)
    public Double avgDeliverSeconds(String tpAccountId, Instant from, Instant to) {
        // FUNCTION('TIMESTAMPDIFF', SECOND, ...) is MySQL-specific. Acceptable —
        // we already pin to MySQL via the JPA dialect (see application.yml).
        TypedQuery<Double> q = em.createQuery("""
                SELECT AVG(FUNCTION('TIMESTAMPDIFF', SECOND, o.createdAt, o.updatedAt))
                  FROM Order o
                 WHERE o.tpAccountId = :tp
                   AND o.status = com.cognilogistic.order.model.OrderStatus.DELIVERED
                   AND (:from IS NULL OR o.createdAt >= :from)
                   AND (:to   IS NULL OR o.createdAt <  :to)
                """, Double.class);
        q.setParameter("tp", tpAccountId);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return q.getSingleResult();
    }

    /** Reference to the entity class so the JPQL type literal {@code Order} resolves at compile time. */
    @SuppressWarnings("unused")
    private static final Class<Order> ORDER_ENTITY = Order.class;
}
