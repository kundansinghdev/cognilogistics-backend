package com.cognilogistic.order;

import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.statemachine.OrderStateMachine;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test of the state machine — covers BR-01 (no skipping), BR-02 (cancellation cutoff)
 * without spinning up Spring or the DB.
 *
 * V3.6: ASSIGNED state removed. CREATED → ACKNOWLEDGED is now a single combined step.
 */
class OrderStateMachineUnitTest {

    private final OrderStateMachine sm = new OrderStateMachine();

    @ParameterizedTest(name = "[{index}] {0} → {1} should be allowed")
    @CsvSource({
            "CREATED, ACKNOWLEDGED",
            "CREATED, CANCELLED",
            "ACKNOWLEDGED, FLEET_CONFIRMED",
            "ACKNOWLEDGED, CANCELLED",
            "FLEET_CONFIRMED, IN_TRANSIT",
            "FLEET_CONFIRMED, CANCELLED",
            "IN_TRANSIT, DELIVERED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(sm.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} → {1} should be rejected as INVALID_TRANSITION (BR-01)")
    @CsvSource({
            // BR-01 no skipping
            "CREATED, FLEET_CONFIRMED",
            "CREATED, IN_TRANSIT",
            "CREATED, DELIVERED",
            "ACKNOWLEDGED, IN_TRANSIT",
            "ACKNOWLEDGED, DELIVERED",
            "FLEET_CONFIRMED, DELIVERED",
            // backwards
            "ACKNOWLEDGED, CREATED",
            // post-terminal
            "DELIVERED, IN_TRANSIT",
            "CANCELLED, ACKNOWLEDGED"
    })
    void rejectsInvalidTransitions(OrderStatus from, OrderStatus to) {
        assertThatThrownBy(() -> sm.requireTransition(from, to))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_TRANSITION);
    }

    @ParameterizedTest(name = "[{index}] cancel from {0} should be CANCELLATION_NOT_ALLOWED (BR-02)")
    @CsvSource({
            "IN_TRANSIT",
            "DELIVERED"
    })
    void br02CancellationCutoff(OrderStatus from) {
        assertThatThrownBy(() -> sm.requireTransition(from, OrderStatus.CANCELLED))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.CANCELLATION_NOT_ALLOWED);
    }
}
