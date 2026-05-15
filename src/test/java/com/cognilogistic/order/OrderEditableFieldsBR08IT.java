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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-08: editable before FLEET_CONFIRMED. After that, no field is editable.
 */
class OrderEditableFieldsBR08IT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void editAfterFleetConfirmed_returns403() throws Exception {
        String phone = "+919810000140";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-1");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000140", "PTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        transition(mockMvc, json, token, o.id(), "confirm-fleet", Map.of("driverName", "Test Driver"));

        mockMvc.perform(patch("/api/v1/orders/" + o.id())
                        .contentType(APPLICATION_JSON)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .content(json.writeValueAsString(Map.of("internalNotes", "late edit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void editBeforeFleetConfirmed_succeeds() throws Exception {
        String phone = "+919810000141";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-2");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000141", "PTL", officeId);

        mockMvc.perform(patch("/api/v1/orders/" + o.id())
                        .contentType(APPLICATION_JSON)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .content(json.writeValueAsString(Map.of("internalNotes", "edited"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalNotes").value("edited"));
    }
}
