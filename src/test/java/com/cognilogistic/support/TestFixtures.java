package com.cognilogistic.support;

import com.cognilogistic.auth.dto.LoginResponse;
import com.cognilogistic.auth.dto.VerifyOtpResponse;
import com.cognilogistic.auth.model.OtpPurpose;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.platform.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test helpers for the auth + order flows. Hides the boilerplate of OTP send/verify/setup-pin
 * so each IT class can focus on the business rule under test.
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** Must match {@code legal_doc_versions} seed (V20260508005) and the web {@code LEGAL_DOC_VERSIONS}. */
    public static final String LEGAL_DOC_VERSION_SEED = "2026-05-08";

    public static final String DEFAULT_SIGNUP_ORG_NAME = "Integration Test Organisation";

    /**
     * Drives the full first-time registration flow (send-otp → verify-otp → setup-pin) for the
     * given phone number with purpose {@code FIRST_LOGIN} and {@code deviceType=WEB}, then returns
     * the freshly issued JWT access token. Asserts HTTP 200 on each step and {@code success=true}
     * on the send-otp step. The OTP is read out of {@link LoggingOtpProvider} (test profile only),
     * so this helper only works against the {@code test} Spring profile.
     *
     * <p>Side effects: inserts rows into {@code users}, {@code auth_credentials},
     * {@code otp_log}, and {@code refresh_tokens}. The IT base class is not transactional —
     * these inserts <b>commit</b> and persist for the lifetime of the shared MySQL container.
     */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json,
                                          LoggingOtpProvider mock,
                                          String phone, String pin, String deviceId) throws Exception {
        // 1. send-otp
        mvc.perform(jsonPost("/api/v1/auth/send-otp",
                        Map.of("phone", phone, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        String otp = mock.lastCodeFor(phone);

        // 2. verify-otp
        MvcResult verifyRes = mvc.perform(jsonPost("/api/v1/auth/verify-otp",
                        Map.of("phone", phone, "otp", otp, "purpose", "FIRST_LOGIN")))
                .andExpect(status().isOk())
                .andReturn();
        VerifyOtpResponse v = readData(json, verifyRes, new TypeReference<>() {});

        // 3. setup-pin — org name + consent versions are mandatory (AuthService.setupPin).
        MvcResult setupRes = mvc.perform(jsonPost("/api/v1/auth/setup-pin",
                        Map.of("tempToken", v.tempToken(), "pin", pin,
                                "deviceId", deviceId, "deviceType", "WEB",
                                "orgName", DEFAULT_SIGNUP_ORG_NAME,
                                "acceptedTermsVersion", LEGAL_DOC_VERSION_SEED,
                                "acceptedPrivacyVersion", LEGAL_DOC_VERSION_SEED)))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse session = readData(json, setupRes, new TypeReference<>() {});
        return session.accessToken();
    }

    /**
     * Calls {@code POST /api/v1/auth/login} for an already-registered user (i.e. the
     * {@link #registerAndLogin} flow has run before for this phone) with
     * {@code deviceType=WEB} and the supplied {@code deviceId}, asserts HTTP 200, and returns
     * the parsed {@code LoginResponse} (access + refresh token pair).
     *
     * <p>Side effects: rotates the refresh-token row and writes a new
     * {@code refresh_tokens} row — committed by the running test transaction.
     */
    public static LoginResponse login(MockMvc mvc, ObjectMapper json,
                                      String phone, String pin, String deviceId) throws Exception {
        MvcResult res = mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("phone", phone, "pin", pin,
                                "deviceId", deviceId, "deviceType", "WEB")))
                .andExpect(status().isOk())
                .andReturn();
        return readData(json, res, new TypeReference<>() {});
    }

    /**
     * Builds a {@code MockMvc} POST request with {@code Content-Type: application/json} and
     * a body serialised from {@code body} via a fresh local {@link ObjectMapper}. Pure helper —
     * does not perform the request, asserts nothing, and writes nothing to the database.
     */
    public static MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        ObjectMapper m = new ObjectMapper();
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(m.writeValueAsString(body));
    }

    /**
     * Reads the {@code data} field out of an {@link com.cognilogistic.platform.api.ApiResponse}
     * envelope and converts it to {@code T} using the supplied Jackson {@link TypeReference}.
     * Use this for happy-path assertions when you only care about the payload, not the envelope's
     * {@code success}/{@code error} fields. Pure helper — no DB writes, no transaction.
     */
    public static <T> T readData(ObjectMapper json, MvcResult res, TypeReference<T> type) throws Exception {
        String body = res.getResponse().getContentAsString();
        Map<String, Object> envelope = json.readValue(body, new TypeReference<>() {});
        return json.convertValue(envelope.get("data"), type);
    }

    /**
     * Reads the full {@link ApiResponse} envelope (success flag, data, error) — use this when
     * the test needs to assert on the error code or the {@code success=false} branch. Pure
     * helper — no DB writes, no transaction.
     */
    public static <T> ApiResponse<T> readEnvelope(ObjectMapper json, MvcResult res) throws Exception {
        String body = res.getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<>() {});
    }
}
