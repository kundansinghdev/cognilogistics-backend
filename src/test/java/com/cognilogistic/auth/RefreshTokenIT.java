package com.cognilogistic.auth;

import com.cognilogistic.auth.dto.TokenPair;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.login;
import static com.cognilogistic.support.TestFixtures.readData;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;

    @Test
    void refresh_rotatesToken_oldRefreshTokenInvalid() throws Exception {
        String phone = "+919810000030";
        registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-R");
        var login = login(mockMvc, json, phone, "1234", "dev-R");

        MvcResult res = mockMvc.perform(jsonPost("/api/v1/auth/refresh",
                        Map.of("refreshToken", login.refreshToken())))
                .andExpect(status().isOk())
                .andReturn();
        TokenPair newPair = readData(json, res, new TypeReference<>() {});
        assertThat(newPair.refreshToken()).isNotEqualTo(login.refreshToken());

        // Reusing the old refresh token must fail
        mockMvc.perform(jsonPost("/api/v1/auth/refresh",
                        Map.of("refreshToken", login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void resetPin_sendOtp_unknownPhone_returnsNotFound() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/reset-pin/send-otp",
                        Map.of("phone", "+919999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PHONE_NOT_REGISTERED"))
                .andExpect(jsonPath("$.error.message").value("Mobile number not found."));
    }

    @Test
    void pinReset_revokesAllRefreshTokens() throws Exception {
        String phone = "+919810000031";
        registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-A");
        var loginA = login(mockMvc, json, phone, "1234", "dev-A");
        // second device login
        registerAndLoginIgnoringFirst(phone, "dev-B");
        var loginB = login(mockMvc, json, phone, "1234", "dev-B");

        // PIN reset flow
        mockMvc.perform(jsonPost("/api/v1/auth/reset-pin/send-otp",
                        Map.of("phone", phone))).andExpect(status().isOk());
        String code = otp.lastCodeFor(phone);
        MvcResult vRes = mockMvc.perform(jsonPost("/api/v1/auth/reset-pin/verify-otp",
                        Map.of("phone", phone, "otp", code))).andReturn();
        var verify = readData(json, vRes, new TypeReference<com.cognilogistic.auth.dto.VerifyOtpResponse>() {});

        mockMvc.perform(jsonPost("/api/v1/auth/reset-pin/set",
                        Map.of("resetToken", verify.tempToken(), "newPin", "5678",
                                "deviceId", "dev-A", "deviceType", "WEB")))
                .andExpect(status().isOk());

        // Both prior refresh tokens must now be revoked
        mockMvc.perform(jsonPost("/api/v1/auth/refresh",
                        Map.of("refreshToken", loginA.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(jsonPost("/api/v1/auth/refresh",
                        Map.of("refreshToken", loginB.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    private void registerAndLoginIgnoringFirst(String phone, String deviceId) throws Exception {
        // The user already exists from the first call. Just login on a different device.
        mockMvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("phone", phone, "pin", "1234",
                                "deviceId", deviceId, "deviceType", "WEB")))
                .andExpect(status().isOk());
    }
}
