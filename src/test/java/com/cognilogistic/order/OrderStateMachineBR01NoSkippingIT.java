package com.cognilogistic.order;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.cognilogistic.support.OrderTestFixtures.createOffice;
import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-01: forward transitions must go through every state in sequence.
 * V3.6: ASSIGNED state removed. CREATED → ACKNOWLEDGED is now a single combined step.
 */
class OrderStateMachineBR01NoSkippingIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void acknowledge_withoutOfficeId_onUnassignedOrder_returns400() throws Exception {
        String phone = "+919810000110";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-2");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000110", "PTL", officeId);

        // Acknowledge with no office_id on an order with no assigned office → VALIDATION_ERROR
        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge", Map.of())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void created_directlyToFleetConfirmed_blocked_byBR01() throws Exception {
        String phone = "+919810000111";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-3");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000111", "PTL", officeId);

        // Cannot jump from CREATED directly to FLEET_CONFIRMED — must go via ACKNOWLEDGED first
        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/confirm-fleet", Map.of())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
    }
}
