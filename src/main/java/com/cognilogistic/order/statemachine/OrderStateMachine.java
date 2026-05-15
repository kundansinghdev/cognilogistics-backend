package com.cognilogistic.order.statemachine;

import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * State machine that validates order status transitions, encoding the core business rules:
 * <ul>
 *   <li>BR-01 — no skipping states (e.g. CREATED cannot jump directly to IN_TRANSIT)</li>
 *   <li>BR-02 — cancellation is blocked once the order is IN_TRANSIT or DELIVERED</li>
 * </ul>
 *
 * <p>V3.6 forward path: {@code CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED}.
 * {@code CANCELLED} is reachable from {@code CREATED}, {@code ACKNOWLEDGED}, and {@code FLEET_CONFIRMED} only.
 * Terminal states ({@code DELIVERED}, {@code CANCELLED}) have no allowed outgoing transitions.
 * No admin override exists for MVP.
 *
 * <p>The transition table is built as an {@link EnumMap} of {@link EnumSet}s for O(1) lookup.
 */
@Component
public class OrderStateMachine {

    // Immutable transition table: each status maps to the set of statuses it can move to.
    // EnumMap + EnumSet (vs HashMap/HashSet) give O(1) lookup with no boxing or hashing
    // overhead — they are backed by a fixed-size array indexed by enum ordinal — and are
    // type-safe: a non-OrderStatus key/value would not even compile.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(OrderStatus.class);
        ALLOWED.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.ACKNOWLEDGED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ACKNOWLEDGED, EnumSet.of(OrderStatus.FLEET_CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.FLEET_CONFIRMED, EnumSet.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.IN_TRANSIT, EnumSet.of(OrderStatus.DELIVERED));
        // Terminal states: no outgoing transitions allowed
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * Returns whether the given state transition is permitted by the rules.
     *
     * @param from the current order status
     * @param to   the desired target status
     * @return {@code true} if the transition is allowed; {@code false} otherwise
     */
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    /**
     * Asserts that the given transition is permitted. Throws a domain exception if not.
     * Provides a more specific error message for BR-02 violations (attempting to cancel an
     * in-transit or delivered order).
     *
     * @param from the current order status
     * @param to   the desired target status
     * @throws com.cognilogistic.platform.api.ApiException with {@code CANCELLATION_NOT_ALLOWED}
     *         when cancellation is blocked (BR-02); with {@code INVALID_TRANSITION} for all other
     *         illegal state changes (BR-01)
     */
    public void requireTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            // BR-02: cancel is blocked once the vehicle is moving
            if (to == OrderStatus.CANCELLED &&
                    (from == OrderStatus.IN_TRANSIT || from == OrderStatus.DELIVERED)) {
                throw new ApiException(ErrorCode.CANCELLATION_NOT_ALLOWED,
                        "Order cannot be cancelled once it is " + from);
            }
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Invalid transition: " + from + " → " + to);
        }
    }
}
