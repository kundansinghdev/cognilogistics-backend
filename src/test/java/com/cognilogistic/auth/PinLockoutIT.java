package com.cognilogistic.auth;

import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR (Auth §4.2 — account lockout): after 5 failed PIN attempts, lock for 30 minutes.
 * 6th attempt with the correct PIN must still return ACCOUNT_LOCKED.
 */
class PinLockoutIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;

    @Test
    void fiveFailures_thenLockout_correctPinStillRejected() throws Exception {
        String phone = "+919810000010";
        registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-A");

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login",
                            Map.of("phone", phone, "pin", "9999",
                                    "deviceId", "dev-A", "deviceType", "WEB")))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt with the correct PIN must hit lockout (server-enforced)
        mockMvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("phone", phone, "pin", "1234",
                                "deviceId", "dev-A", "deviceType", "WEB")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }
}
