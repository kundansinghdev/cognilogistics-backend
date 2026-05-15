package com.cognilogistic.order.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for the Connected Lot view — a derived grouping of orders that share the same
 * vehicle on the same day, with at least 2 orders in the group.
 *
 * <p>Connected Lots are not stored in their own table; they are computed on-the-fly by a
 * native SQL aggregation query against the {@code orders} table. {@link JdbcClient} is used
 * rather than JPA because the query uses {@code GROUP BY} and {@code HAVING COUNT >= 2},
 * which are awkward to express in JPQL and benefit from native SQL projection mapping.
 *
 * <p>The inner record {@link ConnectedLotRow} is the projection type used by JdbcClient's
 * {@code .query(Class).list()} bean mapping.
 */
@Repository
public class ConnectedLotRepository {

    private final JdbcClient jdbc;

    public ConnectedLotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all Connected Lots for the given TP account, optionally filtered by pickup date.
     * Only groups with 2 or more orders sharing the same vehicle registration on the same day
     * are included.
     *
     * @param tpAccountId the owning TP account ID
     * @param date        optional date filter; {@code null} returns all dates
     * @return list of connected lot summary rows, ordered by pickup date descending
     */
    public List<ConnectedLotRow> findConnectedLots(String tpAccountId, LocalDate date) {
        String sql = """
                SELECT od.vehicle_registration AS vehicleNumber,
                       DATE(o.created_at) AS pickupDate,
                       COUNT(*) AS orderCount,
                       SUM(o.weight_kg) AS totalWeightKg,
                       GROUP_CONCAT(o.id ORDER BY o.id) AS orderIds
                FROM orders o
                WHERE o.tp_account_id = :tpAccountId
                  AND o.status IN ('FLEET_CONFIRMED','IN_TRANSIT','DELIVERED')
                  AND o.vehicle_registration IS NOT NULL
                  AND (:date IS NULL OR DATE(o.created_at) = :date)
                GROUP BY od.vehicle_registration, DATE(o.created_at)
                HAVING COUNT(*) >= 2
                ORDER BY pickupDate DESC
                """;

        return jdbc.sql(sql)
                .param("tpAccountId", tpAccountId)
                .param("date", date)
                .query(ConnectedLotRow.class)
                .list();
    }

    /**
     * Projection record for a single connected-lot group returned by the native query.
     *
     * <p>Components:
     * <ul>
     *   <li>{@code vehicleNumber} — the vehicle registration number shared by all orders in the lot</li>
     *   <li>{@code pickupDate} — the common pickup date (derived from {@code DATE(created_at)})</li>
     *   <li>{@code orderCount} — total number of orders in this lot (always >= 2)</li>
     *   <li>{@code totalWeightKg} — sum of all order weights in kilograms</li>
     *   <li>{@code orderIds} — comma-separated list of order IDs, ascending, for LR generation</li>
     * </ul>
     */
    public record ConnectedLotRow(
            String vehicleNumber,
            LocalDate pickupDate,
            int orderCount,
            java.math.BigDecimal totalWeightKg,
            String orderIds
    ) {}
}
