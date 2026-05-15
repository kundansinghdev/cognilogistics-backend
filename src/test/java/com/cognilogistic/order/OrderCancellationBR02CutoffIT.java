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
import static com.cognilogistic.support.OrderTestFixtures.transition;
import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-02: cancellation is allowed up to and including FLEET_CONFIRMED.
 * Once IN_TRANSIT, cancel must return 422 CANCELLATION_NOT_ALLOWED.
 */
class OrderCancellationBR02CutoffIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void cancelAtFleetConfirmed_isAllowed() throws Exception {
        String phone = "+919810000120";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-1");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000120", "PTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        transition(mockMvc, json, token, o.id(), "confirm-fleet", Map.of("driverName", "Test Driver"));

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/cancel",
                        Map.of("reason", "customer changed plan"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cancelInTransit_blocked_returns422() throws Exception {
        String phone = "+919810000121";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-2");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000121", "PTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        transition(mockMvc, json, token, o.id(), "confirm-fleet", Map.of("driverName", "Test Driver"));
        transition(mockMvc, json, token, o.id(), "start-transit");

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/cancel",
                        Map.of("reason", "too late"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CANCELLATION_NOT_ALLOWED"));
    }
}
