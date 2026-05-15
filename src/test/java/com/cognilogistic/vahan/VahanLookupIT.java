package com.cognilogistic.vahan;

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
 * Vahan mock smart-trigger behavior (HTML spec) — prefix WW = warning, NN = not found.
 * Also verifies BR-10 consent gate at the lookup endpoint, not just confirm-fleet.
 */
class VahanLookupIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    @Test
    void lookupWithoutConsent_returns422() throws Exception {
        String phone = "+919810000160";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-1");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000160", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/vahan/lookup",
                        Map.of("orderId", o.id(), "vehicleRegistration", "MH12AB1234"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void lookupWithConsent_returnsMockData() throws Exception {
        String phone = "+919810000161";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-2");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000161", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/vahan/consent",
                        Map.of("orderId", o.id(), "vehicleRegistration", "MH12AB1234",
                                "consentText", "Consent granted", "consentGiven", true))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/vahan/lookup",
                        Map.of("orderId", o.id(), "vehicleRegistration", "MH12AB1234"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.warning").doesNotExist());
    }

    @Test
    void smartMock_WWprefix_returnsWarning() throws Exception {
        String phone = "+919810000162";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-3");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000162", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/vahan/consent",
                        Map.of("orderId", o.id(), "vehicleRegistration", "WW99XX9999",
                                "consentText", "Consent granted", "consentGiven", true))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/vahan/lookup",
                        Map.of("orderId", o.id(), "vehicleRegistration", "WW99XX9999"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warning").exists());
    }

    @Test
    void smartMock_NNprefix_returns503_vahanUnavailable() throws Exception {
        String phone = "+919810000163";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-4");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");
        OrderDto o = createOrder(mockMvc, json, token, "+919811000163", "FTL", officeId);

        mockMvc.perform(jsonPost("/api/v1/vahan/consent",
                        Map.of("orderId", o.id(), "vehicleRegistration", "NN77YY7777",
                                "consentText", "Consent granted", "consentGiven", true))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/vahan/lookup",
                        Map.of("orderId", o.id(), "vehicleRegistration", "NN77YY7777"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("VAHAN_UNAVAILABLE"));
    }
}
