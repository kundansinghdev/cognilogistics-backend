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
 * CHANGE 5: confirm-fleet for FTL requires vahanConsentId; PTL does not require vehicle details.
 */
class OrderConfirmFleetIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void ftlConfirmFleet_withoutConsentLog_returns422() throws Exception {
        String phone = "+919810003001";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-f1");
        String officeId = createOffice(offices, users, phone, "Chennai-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811003001", "FTL", officeId);

        // Use acknowledge with officeId (CHANGE 4 — combined assign + acknowledge)
        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // confirm-fleet without consent → CONSENT_REQUIRED. driverName is now required
        // for both FTL and PTL (it's checked before the consent gate runs).
        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/confirm-fleet",
                        Map.of("vehicleRegistration", "MH12AB5678", "driverName", "Test Driver"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void ptlConfirmFleet_succeeds_withoutVehicleDetails() throws Exception {
        String phone = "+919810003002";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-f2");
        String officeId = createOffice(offices, users, phone, "Hyderabad-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811003002", "PTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // PTL confirm-fleet — vehicle registration is optional, but driverName is now required.
        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/confirm-fleet",
                        Map.of("driverName", "Test Driver"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FLEET_CONFIRMED"));
    }
}
