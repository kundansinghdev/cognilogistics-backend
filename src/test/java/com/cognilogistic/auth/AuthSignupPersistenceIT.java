package com.cognilogistic.auth;

import com.cognilogistic.auth.repository.AuthCredentialsRepository;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the full first-login pipeline persists durable auth state — the
 * same surfaces browser E2E would exercise, verified here against the real DB
 * (Testcontainers MySQL) without a headless browser.
 */
class AuthSignupPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper json;

    @Autowired
    private LoggingOtpProvider otp;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthCredentialsRepository creds;

    @Test
    void signup_writesUserRowPinHashAndE164Phone() throws Exception {
        String phone = "+919810000077";
        registerAndLogin(mockMvc, json, otp, phone, "2468", "persist-signup-1");

        var user = users.findByPhone(phone);
        assertThat(user).isPresent();
        assertThat(user.get().getPhone()).isEqualTo(phone);

        var row = creds.findByUserId(user.get().getId());
        assertThat(row).isPresent();
        assertThat(row.get().getPinHash()).isNotBlank();
    }
}
