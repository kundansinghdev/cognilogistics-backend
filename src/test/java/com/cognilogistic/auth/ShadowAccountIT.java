package com.cognilogistic.auth;

import com.cognilogistic.auth.model.User;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth §4.5 — Shadow accounts cannot log in. send-otp / verify-otp / login must all return
 * 403 SHADOW_ACCOUNT, never an opaque "user not found".
 */
class ShadowAccountIT extends AbstractIntegrationTest {

    @Autowired UserRepository users;

    @Test
    void shadowPhoneRejected_onSendOtp() throws Exception {
        String phone = "+919810000020";

        // Build a shadow user by hand. The legacy {@code User.newShadow(...)}
        // factory was removed in PR A3 (the shadow concept moved to
        // {@code customers.is_shadow}); this test still exercises the
        // legacy {@code users.is_shadow} guard the auth flow keeps for
        // backward compatibility, so we inline the build here.
        User shadow = new User();
        shadow.ensureId();
        shadow.setPhone(phone);
        shadow.setRole(com.cognilogistic.auth.model.UserRole.CUSTOMER);
        shadow.setShadow(true);
        users.save(shadow);

        mockMvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SHADOW_ACCOUNT"));
    }
}
