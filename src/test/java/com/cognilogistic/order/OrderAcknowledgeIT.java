package com.cognilogistic.order;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static com.cognilogistic.support.OrderTestFixtures.createOffice;
import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.readData;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHANGE 4: acknowledge is a combined assign + acknowledge in one call.
 */
class OrderAcknowledgeIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void acknowledge_withOfficeId_setsOfficeAndTransitionsToAcknowledged() throws Exception {
        String phone = "+919810002001";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-a1");
        String officeId = createOffice(offices, users, phone, "Delhi-HQ");

        // Create order — no office assigned yet (CHANGE 1)
        OrderDto created = createOrder(mockMvc, json, token, "+919811002001", "PTL", officeId);
        assertThat(created.assignedOfficeId()).isNull();

        // Acknowledge with officeId — sets office + transitions CREATED → ACKNOWLEDGED
        MvcResult res = mockMvc.perform(jsonPost("/api/v1/orders/" + created.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto acked = readData(json, res, new TypeReference<>() {});
        assertThat(acked.status().name()).isEqualTo("ACKNOWLEDGED");
        assertThat(acked.assignedOfficeId()).isEqualTo(officeId);
    }

    @Test
    void acknowledge_withoutOfficeId_onUnassignedOrder_returns400() throws Exception {
        String phone = "+919810002002";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-a2");
        String officeId = createOffice(offices, users, phone, "Mumbai-HQ");

        OrderDto created = createOrder(mockMvc, json, token, "+919811002002", "PTL", officeId);

        // Acknowledge with empty body — order has no office → should fail
        mockMvc.perform(jsonPost("/api/v1/orders/" + created.id() + "/acknowledge", Map.of())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
