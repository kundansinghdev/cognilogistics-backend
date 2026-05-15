package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>All multi-order queries are scoped by {@code tpAccountId} to prevent cross-account data
 * access. The {@link #search} method supports optional filtering; passing {@code null} for any
 * parameter disables that filter.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Retrieves all orders belonging to a specific customer.
     * Used by the customer portal to list the authenticated customer's own orders.
     *
     * @param customerId the customer's ID
     * @return all orders for that customer, in repository default order
     */
    List<Order> findAllByCustomerId(String customerId);

    /**
     * Filtered order search scoped to a TP account. All filter parameters are optional;
     * passing {@code null} for a parameter means "no filter on that field".
     *
     * @param tpAccountId the owning TP account (always required)
     * @param status      optional status filter
     * @param officeId    optional branch office filter
     * @param from        optional inclusive lower bound on {@code createdAt}
     * @param to          optional exclusive upper bound on {@code createdAt}
     * @return matching orders ordered by {@code createdAt DESC}
     */
    /**
     * Counts all orders ever assigned to a given office (any status).
     * Used by {@link com.cognilogistic.user.service.OfficeService} to populate
     * the {@code orderCount} field in {@link com.cognilogistic.user.dto.OfficeResponseDto}.
     *
     * @param officeId the branch office ID
     * @return total number of orders assigned to that office
     */
    long countByAssignedOfficeId(String officeId);

    /**
     * Counts existing orders for a TP whose {@code order_no} starts with the given prefix.
     * Used by the order-number generator to pick the next sequence number for the day:
     * {@code prefix = "COG-20260508-"}, count = 3 → next number is {@code COG-20260508-0004}.
     *
     * <p>Race note: under concurrent creates, two callers can read the same count. The
     * DB-level UNIQUE on {@code (tp_account_id, order_no)} rejects the loser, who retries.
     */
    long countByTpAccountIdAndOrderNoStartingWith(String tpAccountId, String orderNoPrefix);

    /**
     * Checks whether any non-terminal orders (not DELIVERED, not CANCELLED) are assigned
     * to the given office. Used to block soft-deactivation when active work is in progress (BR-OFF-06).
     *
     * @param officeId the branch office ID
     * @param statuses the set of terminal statuses to exclude from the "active" count
     * @return {@code true} if at least one active (non-terminal) order exists
     */
    @Query("""
            SELECT COUNT(o) > 0 FROM Order o
            WHERE o.assignedOfficeId = :officeId
              AND o.status NOT IN :statuses
            """)
    boolean existsActiveOrdersByOfficeId(@Param("officeId") String officeId,
                                         @Param("statuses") java.util.Collection<com.cognilogistic.order.model.OrderStatus> statuses);

    @Query("""
            SELECT o FROM Order o
            WHERE o.tpAccountId = :tp
              AND (:status IS NULL OR o.status = :status)
              AND (:officeId IS NULL OR o.assignedOfficeId = :officeId)
              AND (:restrictToAssignedOffices = false OR o.assignedOfficeId IN :assignedOfficeIds)
              AND (:deliveryType IS NULL OR o.deliveryType = :deliveryType)
              AND (:requestedVehicleType IS NULL OR o.requestedVehicleType = :requestedVehicleType)
              AND (:pickupFrom IS NULL OR o.pickupDate >= :pickupFrom)
              AND (:pickupTo IS NULL OR o.pickupDate <= :pickupTo)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            ORDER BY o.createdAt DESC
            """)
    List<Order> search(@Param("tp") String tpAccountId,
                       @Param("status") OrderStatus status,
                       @Param("officeId") String officeId,
                       @Param("restrictToAssignedOffices") boolean restrictToAssignedOffices,
                       @Param("assignedOfficeIds") List<String> assignedOfficeIds,
                       @Param("deliveryType") DeliveryType deliveryType,
                       @Param("requestedVehicleType") String requestedVehicleType,
                       @Param("pickupFrom") LocalDate pickupFrom,
                       @Param("pickupTo") LocalDate pickupTo,
                       @Param("from") Instant from,
                       @Param("to") Instant to);

    /**
     * Returns every order in the TP that shares the same vehicle registration AND
     * pickup date — i.e. the "connected lot" for the order-detail view.
     *
     * <p>The order-detail UI calls this to render a "Connected Orders" panel
     * grouping multi-stop loads. When fewer than 2 orders match, the UI suppresses
     * the panel — but the API still returns the singleton so the FE can use it
     * uniformly.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.tpAccountId = :tp
              AND o.vehicleRegistration = :vehicleRegistration
              AND o.pickupDate = :pickupDate
            ORDER BY o.createdAt ASC
            """)
    List<Order> findLot(@Param("tp") String tpAccountId,
                        @Param("vehicleRegistration") String vehicleRegistration,
                        @Param("pickupDate") LocalDate pickupDate);

    /**
     * Aggregates order counts per assigned office for branch KPI cards — one grouped query
     * instead of N {@link #countByAssignedOfficeId} calls.
     *
     * @param tpAccountId      owning TP
     * @param officeIds        office UUIDs to include (empty list → caller should skip query)
     * @param transitStatuses  typically {@code IN_TRANSIT} + {@code FLEET_CONFIRMED}
     * @param delivered        {@link OrderStatus#DELIVERED}
     * @param expressDelivery  {@link DeliveryType#EXPRESS} (combined with {@code express} flag in JPQL)
     * @return each row: {@code [officeId, total, inTransitBucket, delivered, express]}
     */
    @Query("""
            SELECT o.assignedOfficeId, COUNT(o),
                   SUM(CASE WHEN o.status IN :transitStatuses THEN 1 ELSE 0 END),
                   SUM(CASE WHEN o.status = :delivered THEN 1 ELSE 0 END),
                   SUM(CASE WHEN o.express = true OR o.deliveryType = :expressDelivery THEN 1 ELSE 0 END)
            FROM Order o
            WHERE o.tpAccountId = :tpAccountId AND o.assignedOfficeId IN :officeIds
            GROUP BY o.assignedOfficeId
            """)
    List<Object[]> aggregateMetricsByOfficeIds(@Param("tpAccountId") String tpAccountId,
                                               @Param("officeIds") List<String> officeIds,
                                               @Param("transitStatuses") List<OrderStatus> transitStatuses,
                                               @Param("delivered") OrderStatus delivered,
                                               @Param("expressDelivery") DeliveryType expressDelivery);

    /**
     * Counts orders per {@code company_id} for Company Master list KPIs.
     */
    @Query("""
            SELECT o.companyId, COUNT(o)
            FROM Order o
            WHERE o.tpAccountId = :tpAccountId AND o.companyId IN :companyIds
            GROUP BY o.companyId
            """)
    List<Object[]> countOrdersGroupedByCompanyIds(@Param("tpAccountId") String tpAccountId,
                                                  @Param("companyIds") List<String> companyIds);

    /**
     * Orders linked to a company (any status), scoped by TP.
     */
    long countByTpAccountIdAndCompanyId(String tpAccountId, String companyId);
}
