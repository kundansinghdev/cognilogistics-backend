package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.OrderStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link OrderStatusLog} — the append-only audit log
 * of order status transitions (BR-06).
 */
public interface OrderStatusLogRepository extends JpaRepository<OrderStatusLog, String> {

    /**
     * Returns all status log entries for an order in chronological order.
     * The first row always has {@code fromStatus = null} (the initial CREATED entry).
     *
     * @param orderId the order whose history is requested
     * @return log entries ordered by {@code triggeredAt} ascending
     */
    List<OrderStatusLog> findByOrderIdOrderByTriggeredAtAsc(String orderId);
}
