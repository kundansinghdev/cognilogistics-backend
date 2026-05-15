package com.cognilogistic.auth;

import com.cognilogistic.auth.dto.LoginResponse;
import com.cognilogistic.auth.dto.VerifyOtpResponse;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.cognilogistic.support.TestFixtures.readData;
import static com.cognilogistic.support.TestFixtures.DEFAULT_SIGNUP_ORG_NAME;
import static com.cognilogistic.support.TestFixtures.LEGAL_DOC_VERSION_SEED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;

    @Test
    void login_malformedJson_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void firstLogin_OtpVerifySetupPin_returnsTokens() throws Exception {
        String phone = "+919810000001";

        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String code = otp.lastCodeFor(phone);
        assertThat(code).hasSize(6);

        MvcResult vRes = mockMvc.perform(jsonPost("/api/v1/auth/verify-otp",
                        Map.of("phone", phone, "otp", code, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andReturn();
        VerifyOtpResponse v = readData(json, vRes, new TypeReference<>() {});

        MvcResult sRes = mockMvc.perform(jsonPost("/api/v1/auth/setup-pin",
                        Map.of("tempToken", v.tempToken(), "pin", "1234",
                                "deviceId", "dev-1", "deviceType", "WEB",
                                "orgName", DEFAULT_SIGNUP_ORG_NAME,
                                "acceptedTermsVersion", LEGAL_DOC_VERSION_SEED,
                                "acceptedPrivacyVersion", LEGAL_DOC_VERSION_SEED)))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse session = readData(json, sRes, new TypeReference<>() {});

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
    }

    @Test
    void invalidPin_returns400() throws Exception {
        String phone = "+919810000002";
        // need to setup first to get past OTP guards
        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN"))).andExpect(status().isOk());
        String code = otp.lastCodeFor(phone);
        MvcResult vRes = mockMvc.perform(jsonPost("/api/v1/auth/verify-otp",
                        Map.of("phone", phone, "otp", code, "purpose", "FIRST_LOGIN")))
                .andReturn();
        VerifyOtpResponse v = readData(json, vRes, new TypeReference<>() {});

        // PIN validation happens via Bean Validation @Pattern → VALIDATION_ERROR
        mockMvc.perform(jsonPost("/api/v1/auth/setup-pin",
                        Map.of("tempToken", v.tempToken(), "pin", "abc",
                                "deviceId", "dev-2", "deviceType", "WEB",
                                "orgName", DEFAULT_SIGNUP_ORG_NAME,
                                "acceptedTermsVersion", LEGAL_DOC_VERSION_SEED,
                                "acceptedPrivacyVersion", LEGAL_DOC_VERSION_SEED)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void wrongOtp_returns400_invalidOtp() throws Exception {
        String phone = "+919810000003";
        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN"))).andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/auth/verify-otp",
                        Map.of("phone", phone, "otp", "000000", "purpose", "FIRST_LOGIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));
    }

    @Test
    void otpRateLimit_secondSendWithin60s_returns429() throws Exception {
        String phone = "+919810000004";
        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN"))).andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void checkPhone_unknownNumber_returnsNotRegistered() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/check-phone", Map.of("phone", "+919000000099")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registered").value(false))
                .andExpect(jsonPath("$.data.loginOnly").value(false));
    }

    @Test
    void checkPhone_seedAdmin_returnsLoginOnly() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/check-phone", Map.of("phone", "+919999900001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.loginOnly").value(true));
    }

    @Test
    void sendOtp_seedAdmin_returnsAdminLoginOnly() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", "+919999900001", "purpose", "FIRST_LOGIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_LOGIN_ONLY"));
    }

    @Test
    void resetPin_sendOtp_seedAdmin_returnsAdminLoginOnly() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/reset-pin/send-otp",
                        Map.of("phone", "+919999900001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_LOGIN_ONLY"));
    }

    @Test
    void login_seedAdmin_withPin_returnsTokens() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("phone", "+919999900001", "pin", "1234",
                                "deviceId", "dev-admin", "deviceType", "WEB")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("COGNILOGISTIC_ADMIN"));
    }

    @Test
    void checkPhone_afterSetupPin_returnsRegistered() throws Exception {
        String phone = "+919810000050";
        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk());
        String code = otp.lastCodeFor(phone);
        MvcResult vRes = mockMvc.perform(jsonPost("/api/v1/auth/verify-otp",
                        Map.of("phone", phone, "otp", code, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk())
                .andReturn();
        VerifyOtpResponse v = readData(json, vRes, new TypeReference<>() {});

        mockMvc.perform(jsonPost("/api/v1/auth/setup-pin",
                        Map.of("tempToken", v.tempToken(), "pin", "1234",
                                "deviceId", "dev-check-phone", "deviceType", "WEB",
                                "orgName", DEFAULT_SIGNUP_ORG_NAME,
                                "acceptedTermsVersion", LEGAL_DOC_VERSION_SEED,
                                "acceptedPrivacyVersion", LEGAL_DOC_VERSION_SEED)))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/auth/check-phone", Map.of("phone", phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.loginOnly").value(false));
    }
}
