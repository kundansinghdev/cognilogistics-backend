package com.cognilogistic.order.service;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.order.dto.OrderDashboardConversionDto;
import com.cognilogistic.order.dto.OrderDashboardDto;
import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * DB-backed aggregations for the TP orders summary dashboard. All headline figures
 * (status pills, cards, conversion bar) are computed with {@code COUNT} queries scoped
 * to the caller's TP — never by summing rows in the browser.
 */
@Service
public class OrderDashboardService {

    private static final Logger log = LoggerFactory.getLogger(OrderDashboardService.class);

    /** Rolling creation window for the conversion strip — matches product copy "30 days". */
    private static final int CONVERSION_WINDOW_DAYS = 30;

    /** Guards pathological range scans on {@code pickup_date}. */
    private static final int MAX_PICKUP_SPAN_DAYS = 366;

    private static final int MAX_REQUESTED_VEHICLE_LEN = 50;

    private final OrderAccessScope accessScope;

    @PersistenceContext
    private EntityManager em;

    public OrderDashboardService(OrderAccessScope accessScope) {
        this.accessScope = accessScope;
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Builds dashboard metrics for the authenticated TP user.
     *
     * @param officeId               optional branch scope (must belong to the caller's TP)
     * @param deliveryType           optional {@link DeliveryType} filter
     * @param requestedVehicleTypeRaw optional exact match on {@code order_details.requested_vehicle_type}
     * @param pickupDateFrom         optional inclusive lower bound on {@code pickup_date}
     * @param pickupDateTo           optional inclusive upper bound on {@code pickup_date}
     */
    @Transactional(readOnly = true)
    public OrderDashboardDto dashboard(AuthPrincipal me,
                                       String officeIdRaw,
                                       DeliveryType deliveryType,
                                       String requestedVehicleTypeRaw,
                                       LocalDate pickupDateFrom,
                                       LocalDate pickupDateTo) {
        requireTp(me);
        String officeId = accessScope.resolveOfficeFilter(me, officeIdRaw, me.tpAccountId());
        List<String> assignedOnly = accessScope.officeIdsForQuery(me, officeId);
        validatePickupWindow(pickupDateFrom, pickupDateTo);
        String requestedVehicleType = normalizeVehicleFilter(requestedVehicleTypeRaw);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        String tp = me.tpAccountId();

        Map<OrderStatus, Long> statusCounts = OrderDashboardDto.emptyStatusCounts();
        CriteriaQuery<Object[]> groupQ = cb.createQuery(Object[].class);
        Root<Order> groupRoot = groupQ.from(Order.class);
        List<Predicate> base = basePredicates(cb, groupRoot, tp, officeId, assignedOnly, deliveryType,
                requestedVehicleType, pickupDateFrom, pickupDateTo);
        groupQ.multiselect(groupRoot.get("status"), cb.count(groupRoot));
        groupQ.where(base.toArray(Predicate[]::new));
        groupQ.groupBy(groupRoot.get("status"));
        for (Object[] row : em.createQuery(groupQ).getResultList()) {
            OrderStatus st = (OrderStatus) row[0];
            // COUNT may surface as Long or Integer depending on provider / DB driver.
            long c = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCounts.put(st, c);
        }

        long allOrdersTotal = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long inTransit = statusCounts.getOrDefault(OrderStatus.IN_TRANSIT, 0L);
        long delivered = statusCounts.getOrDefault(OrderStatus.DELIVERED, 0L);

        long expressTotal = countOrders((b, r) -> {
            List<Predicate> p = new ArrayList<>(basePredicates(b, r, tp, officeId, assignedOnly, deliveryType,
                    requestedVehicleType, pickupDateFrom, pickupDateTo));
            p.add(expressPredicate(b, r));
            return p;
        });

        long expressPendingNew = countOrders((b, r) -> {
            List<Predicate> p = new ArrayList<>(basePredicates(b, r, tp, officeId, assignedOnly, deliveryType,
                    requestedVehicleType, pickupDateFrom, pickupDateTo));
            p.add(expressPredicate(b, r));
            p.add(b.equal(r.get("status"), OrderStatus.CREATED));
            return p;
        });

        Instant now = Instant.now();
        Instant windowStart = now.minus(CONVERSION_WINDOW_DAYS, ChronoUnit.DAYS);

        OrderDashboardConversionDto conversion = buildConversion(tp, officeId, assignedOnly, deliveryType,
                requestedVehicleType, pickupDateFrom, pickupDateTo, windowStart, now);

        log.info("Order dashboard built | tp={} | userId={} | officeId={} | allOrders={} | inTransit={} | delivered={} | express={} | expressPendingNew={} | conv30={}/{}",
                suffix(tp),
                suffix(me.userId()),
                suffix(officeId),
                allOrdersTotal,
                inTransit,
                delivered,
                expressTotal,
                expressPendingNew,
                conversion.deliveredInWindow(),
                conversion.ordersCreatedInWindow());

        return new OrderDashboardDto(
                statusCounts,
                allOrdersTotal,
                inTransit,
                delivered,
                expressTotal,
                expressPendingNew,
                conversion);
    }

    private OrderDashboardConversionDto buildConversion(String tp,
                                                        String officeId,
                                                        List<String> assignedOfficeIds,
                                                        DeliveryType deliveryType,
                                                        String requestedVehicleType,
                                                        LocalDate pickupDateFrom,
                                                        LocalDate pickupDateTo,
                                                        Instant windowStart,
                                                        Instant now) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> denomQ = cb.createQuery(Long.class);
        Root<Order> r = denomQ.from(Order.class);
        List<Predicate> p = new ArrayList<>(basePredicates(cb, r, tp, officeId, assignedOfficeIds, deliveryType,
                requestedVehicleType, pickupDateFrom, pickupDateTo));
        p.add(cb.greaterThanOrEqualTo(r.get("createdAt"), windowStart));
        p.add(cb.lessThanOrEqualTo(r.get("createdAt"), now));
        denomQ.select(cb.count(r));
        denomQ.where(p.toArray(Predicate[]::new));
        long ordersCreatedInWindow = em.createQuery(denomQ).getSingleResult();

        CriteriaBuilder cb2 = em.getCriteriaBuilder();
        CriteriaQuery<Long> numQ = cb2.createQuery(Long.class);
        Root<Order> r2 = numQ.from(Order.class);
        List<Predicate> p2 = new ArrayList<>(basePredicates(cb2, r2, tp, officeId, assignedOfficeIds, deliveryType,
                requestedVehicleType, pickupDateFrom, pickupDateTo));
        p2.add(cb2.greaterThanOrEqualTo(r2.get("createdAt"), windowStart));
        p2.add(cb2.lessThanOrEqualTo(r2.get("createdAt"), now));
        p2.add(cb2.equal(r2.get("status"), OrderStatus.DELIVERED));
        numQ.select(cb2.count(r2));
        numQ.where(p2.toArray(Predicate[]::new));
        long deliveredInWindow = em.createQuery(numQ).getSingleResult();

        double ratePercent = ordersCreatedInWindow == 0
                ? 0.0d
                : Math.round(1000.0 * deliveredInWindow / ordersCreatedInWindow) / 10.0d;

        return new OrderDashboardConversionDto(
                CONVERSION_WINDOW_DAYS,
                ordersCreatedInWindow,
                deliveredInWindow,
                ratePercent);
    }

    /**
     * Runs {@code COUNT(*)} with predicates built against the same {@link Root} as the query
     * (JPA requires predicate paths to reference that root).
     */
    private long countOrders(BiFunction<CriteriaBuilder, Root<Order>, List<Predicate>> predicateFactory) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Order> r = cq.from(Order.class);
        cq.select(cb.count(r));
        cq.where(predicateFactory.apply(cb, r).toArray(Predicate[]::new));
        return em.createQuery(cq).getSingleResult();
    }

    private static Predicate expressPredicate(CriteriaBuilder cb, Root<Order> r) {
        return cb.or(
                cb.isTrue(r.get("express")),
                cb.equal(r.get("deliveryType"), DeliveryType.EXPRESS));
    }

    private List<Predicate> basePredicates(CriteriaBuilder cb,
                                           Root<Order> r,
                                           String tpId,
                                           String officeId,
                                           List<String> assignedOfficeIds,
                                           DeliveryType deliveryType,
                                           String requestedVehicleType,
                                           LocalDate pickupFrom,
                                           LocalDate pickupTo) {
        List<Predicate> p = new ArrayList<>();
        p.add(cb.equal(r.get("tpAccountId"), tpId));
        if (officeId != null) {
            p.add(cb.equal(r.get("assignedOfficeId"), officeId));
        } else if (assignedOfficeIds != null && !assignedOfficeIds.isEmpty()) {
            p.add(r.get("assignedOfficeId").in(assignedOfficeIds));
        }
        if (deliveryType != null) {
            p.add(cb.equal(r.get("deliveryType"), deliveryType));
        }
        if (requestedVehicleType != null) {
            p.add(cb.equal(r.get("requestedVehicleType"), requestedVehicleType));
        }
        if (pickupFrom != null) {
            p.add(cb.greaterThanOrEqualTo(r.get("pickupDate"), pickupFrom));
        }
        if (pickupTo != null) {
            p.add(cb.lessThanOrEqualTo(r.get("pickupDate"), pickupTo));
        }
        return p;
    }

    private void validatePickupWindow(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "pickupDateTo must be on or after pickupDateFrom",
                        Map.of("fields", Map.of(
                                "pickupDateFrom", "pickupDateFrom",
                                "pickupDateTo", "pickupDateTo")));
            }
            long span = ChronoUnit.DAYS.between(from, to);
            if (span > MAX_PICKUP_SPAN_DAYS) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Pickup date range cannot exceed " + MAX_PICKUP_SPAN_DAYS + " days",
                        Map.of("fields", Map.of("pickupDateTo", "range too large")));
            }
        }
    }

    private String normalizeVehicleFilter(String raw) {
        String s = blankToNull(raw);
        if (s == null) {
            return null;
        }
        if (s.length() > MAX_REQUESTED_VEHICLE_LEN) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "requestedVehicleType must be at most " + MAX_REQUESTED_VEHICLE_LEN + " characters",
                    Map.of("fields", Map.of("requestedVehicleType", "too long")));
        }
        return s;
    }

    private void requireTp(AuthPrincipal me) {
        if (me == null || me.tpAccountId() == null || me.tpAccountId().isBlank()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not associated with a TP account");
        }
    }

}
