package com.cognilogistic.order;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.integrationclient.vahan.repository.VahanConsentLogRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-09: FTL orders require vehicle registration on confirm-fleet; PTL does not.
 * BR-10: a vahan_consent_log row must exist for the order+vehicle before confirm-fleet succeeds.
 */
class OrderFleetConfirmBR09BR10IT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;
    @Autowired VahanConsentLogRepository consentLog;

    @Test
    void ftlConfirmFleet_withoutConsentLog_returns422_consentRequired() throws Exception {
        String phone = "+919810000150";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-1");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000150", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/confirm-fleet",
                        Map.of("vehicleRegistration", "MH12AB1234", "driverName", "Test Driver"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void ftlConfirmFleet_withConsentLog_succeeds_inMockMode() throws Exception {
        String phone = "+919810000151";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-2");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000151", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // record consent first (BR-10)
        mockMvc.perform(jsonPost("/api/v1/vahan/consent",
                        Map.of(
                                "orderId", o.id(),
                                "vehicleRegistration", "MH12AB1234",
                                "consentText", "User consents to Vahan lookup",
                                "consentGiven", true
                        ))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // BR-10: consent row should be written even in mock mode
        assertThat(consentLog.findByOrderIdAndVehicleRegistration(o.id(), "MH12AB1234")).isNotEmpty();

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/confirm-fleet",
                        Map.of("vehicleRegistration", "MH12AB1234", "driverName", "Test Driver"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FLEET_CONFIRMED"));
    }

    @Test
    void ptlConfirmFleet_doesNotRequireVehicleOrConsent() throws Exception {
        String phone = "+919810000152";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-3");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000152", "PTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        // PTL: vehicle registration is optional, but driverName is required.
        transition(mockMvc, json, token, o.id(), "confirm-fleet", Map.of("driverName", "Test Driver"));
    }
}
