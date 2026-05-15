package com.cognilogistic.order;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.repository.OrderStatusLogRepository;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.cognilogistic.support.OrderTestFixtures.createOffice;
import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.OrderTestFixtures.transition;
import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-06: every status change — including the initial CREATED — must produce an order_status_log row.
 * This test runs a full PTL lifecycle and verifies the status log.
 *
 * V3.6: ASSIGNED removed. Lifecycle is now CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED.
 */
class OrderHappyPathBR06IT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;
    @Autowired OrderStatusLogRepository statusLog;

    @Test
    void ptlOrder_fullLifecycle_logsEachTransition() throws Exception {
        String phone = "+919810000100";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-1");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");

        OrderDto created = createOrder(mockMvc, json, token, "+919811000100", "PTL", officeId);
        assertThat(created.status().name()).isEqualTo("CREATED");

        // V3.6: acknowledge sets office + transitions CREATED → ACKNOWLEDGED in one call
        mockMvc.perform(jsonPost("/api/v1/orders/" + created.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // PTL: vehicle registration optional, driverName required.
        transition(mockMvc, json, token, created.id(), "confirm-fleet", Map.of("driverName", "Test Driver"));
        transition(mockMvc, json, token, created.id(), "start-transit");
        transition(mockMvc, json, token, created.id(), "deliver");

        var logs = statusLog.findByOrderIdOrderByTriggeredAtAsc(created.id());
        assertThat(logs).extracting(l -> l.getToStatus().name())
                .containsExactly("CREATED", "ACKNOWLEDGED", "FLEET_CONFIRMED", "IN_TRANSIT", "DELIVERED");

        // BR-06: from_status is NULL on the initial CREATED row
        assertThat(logs.get(0).getFromStatus()).isNull();
        assertThat(logs.get(0).getTriggeredAt()).isNotNull();
    }
}
