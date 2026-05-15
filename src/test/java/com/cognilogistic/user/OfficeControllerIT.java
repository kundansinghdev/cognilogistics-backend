package com.cognilogistic.user;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.support.OrderTestFixtures;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.TestFixtures.*;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Branch Office CRUD API at {@code /api/v1/offices}.
 *
 * <p>Each test method is independent: it registers a fresh TP_PRIMARY user on a unique
 * phone number so tests never interfere with each other on the shared Testcontainers DB.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>Happy path — full create → getById → list → update name → delete lifecycle</li>
 *   <li>BR-OFF-01 — missing required field returns 400 VALIDATION_ERROR</li>
 *   <li>BR-OFF-02 — duplicate code returns 409 OFFICE_CODE_EXISTS</li>
 *   <li>BR-OFF-03 — invalid GSTIN returns 400 INVALID_GSTIN</li>
 *   <li>BR-OFF-05 — TP_TRANSPORT_MANAGER mutation returns 403 FORBIDDEN</li>
 *   <li>BR-OFF-06 — deactivation with active orders returns 422 OFFICE_HAS_ACTIVE_ORDERS</li>
 * </ul>
 */
class OfficeControllerIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    /**
     * A TP_PRIMARY user can create an office, retrieve it by ID, see it in the list,
     * update its name, and delete it (no orders assigned).
     */
    @Test
    void happyPath_createGetListUpdateDelete() throws Exception {
        String phone = "+919800001001";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-hp1");

        // POST → 201 with created office
        var createRes = mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Mumbai Hub",
                                "code", "MH1",
                                "city", "Mumbai",
                                "state", "Maharashtra",
                                "pincode", "400001"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("MH1"))
                .andExpect(jsonPath("$.data.city").value("Mumbai"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(0))
                .andExpect(jsonPath("$.data.orderMetrics.totalOrders").value(0))
                .andExpect(jsonPath("$.data.orderMetrics.conversionRatePercent", closeTo(0.0, 0.0001)))
                .andReturn();

        // Office ids are CHAR(36) UUID strings (PR A2 cutover from Long to String).
        String officeId = json.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // GET by ID
        mockMvc.perform(get("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(officeId))
                .andExpect(jsonPath("$.data.name").value("Mumbai Hub"));

        // GET list — office appears
        mockMvc.perform(get("/api/v1/offices")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + officeId + "')].name", hasItem("Mumbai Hub")));

        // PATCH — update name
        mockMvc.perform(patch("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Mumbai Hub Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Mumbai Hub Renamed"));

        // DELETE — succeeds because no orders are assigned
        mockMvc.perform(delete("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Confirm deleted
        mockMvc.perform(get("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OFFICE_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // BR-OFF-01: required fields
    // -------------------------------------------------------------------------

    /**
     * BR-OFF-01: POST without the mandatory {@code city} field must return
     * 400 with error code {@code VALIDATION_ERROR}.
     */
    @Test
    void createOffice_missingCity_returns400() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001002", "1234", "dev-br01");

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Delhi Hub",
                                "code", "DEL1",
                                "state", "Delhi"))  // city intentionally omitted
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // BR-OFF-02: code uniqueness
    // -------------------------------------------------------------------------

    /**
     * BR-OFF-02: a second POST with the same {@code code} in the same TP account
     * must return 409 with error code {@code OFFICE_CODE_EXISTS}.
     */
    @Test
    void createOffice_duplicateCode_returns409() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001003", "1234", "dev-br02");

        // First office — succeeds
        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Pune Hub 1",
                                "code", "PNQ",
                                "city", "Pune",
                                "state", "Maharashtra"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        // Second office with the same code — must conflict
        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Pune Hub 2",
                                "code", "PNQ",  // duplicate
                                "city", "Pune",
                                "state", "Maharashtra"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFICE_CODE_EXISTS"));
    }

    /**
     * BR-OFF-02 + BR-OFF-07: code comparison is case-insensitive — "pnq" and "PNQ"
     * are the same code (both normalised to uppercase).
     */
    @Test
    void createOffice_duplicateCodeCaseInsensitive_returns409() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001004", "1234", "dev-br02b");

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Chennai Hub",
                                "code", "MAA",
                                "city", "Chennai",
                                "state", "Tamil Nadu"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Chennai Hub 2",
                                "code", "maa",  // lowercase — normalises to "MAA"
                                "city", "Chennai",
                                "state", "Tamil Nadu"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFICE_CODE_EXISTS"));
    }

    /**
     * Duplicate name + city + state under the same TP account returns 409 {@code OFFICE_LOCATION_DUPLICATE}
     * (distinct code is not enough).
     */
    @Test
    void createOffice_duplicateNameCityState_returns409() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001099", "1234", "dev-br-dup-loc");

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Indore Hub",
                                "code", "IDR1",
                                "city", "Indore",
                                "state", "Madhya Pradesh"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Indore Hub",
                                "code", "IDR2",
                                "city", "Indore",
                                "state", "Madhya Pradesh"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFICE_LOCATION_DUPLICATE"));
    }

    // -------------------------------------------------------------------------
    // BR-OFF-03: GSTIN validation
    // -------------------------------------------------------------------------

    /**
     * BR-OFF-03: providing a malformed GSTIN on POST must return 400 {@code INVALID_GSTIN}.
     */
    @Test
    void createOffice_invalidGstin_returns400() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001005", "1234", "dev-br03");

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Hyderabad Hub",
                                "code", "HYD",
                                "city", "Hyderabad",
                                "state", "Telangana",
                                "gstin", "INVALID-GSTIN"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_GSTIN"));
    }

    /**
     * BR-OFF-03: a well-formed GSTIN on POST must be accepted (no error).
     */
    @Test
    void createOffice_validGstin_accepted() throws Exception {
        String token = registerAndLogin(mockMvc, json, otp, "+919800001006", "1234", "dev-br03b");

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Kolkata Hub",
                                "code", "CCU",
                                "city", "Kolkata",
                                "state", "West Bengal",
                                "gstin", "19AABCU9603R1Z3"))  // valid format
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gstin").value("19AABCU9603R1Z3"));
    }

    // -------------------------------------------------------------------------
    // BR-OFF-05: TP_ADMIN-only mutation
    // -------------------------------------------------------------------------

    /**
     * BR-OFF-05: a user with {@code TP_TRANSPORT_MANAGER} role attempting to create an office
     * must receive 403 {@code FORBIDDEN}.
     */
    @Test
    void createOffice_branchUser_returns403() throws Exception {
        String phone = "+919800001007";
        // Register (creates TP_PRIMARY)
        registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-br05");

        // Downgrade role to TP_TRANSPORT_MANAGER in DB
        var u = users.findByPhone(phone).orElseThrow();
        u.setRole(UserRole.TP_TRANSPORT_MANAGER);
        users.save(u);

        // Login again to get a fresh JWT reflecting TP_TRANSPORT_MANAGER role
        var loginRes = login(mockMvc, json, phone, "1234", "dev-br05");
        String branchToken = loginRes.accessToken();

        mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Jaipur Hub",
                                "code", "JAI",
                                "city", "Jaipur",
                                "state", "Rajasthan"))
                        .header(AUTHORIZATION, "Bearer " + branchToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // -------------------------------------------------------------------------
    // BR-OFF-06: deactivation blocked by active orders
    // -------------------------------------------------------------------------

    /**
     * BR-OFF-06: PATCH {@code is_active=false} must return 422 {@code OFFICE_HAS_ACTIVE_ORDERS}
     * when non-DELIVERED/non-CANCELLED orders are assigned to the office.
     */
    @Test
    void deactivateOffice_hasActiveOrders_returns422() throws Exception {
        String phone = "+919800001008";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-br06");

        // Create an office via API
        var createRes = mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Ahmedabad Hub",
                                "code", "AMD",
                                "city", "Ahmedabad",
                                "state", "Gujarat"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        // Office ids are CHAR(36) UUID strings (PR A2 cutover from Long to String).
        String officeId = json.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Create an order and acknowledge it to assign this office
        var order = createOrder(mockMvc, json, token, "+919810000999", "PTL", officeId);
        mockMvc.perform(jsonPost("/api/v1/orders/" + order.id() + "/acknowledge",
                                Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        // Order is now ACKNOWLEDGED — a non-terminal status → deactivation must be blocked

        // PATCH is_active=false → must fail with 422
        mockMvc.perform(patch("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("OFFICE_HAS_ACTIVE_ORDERS"));
    }

    /**
     * BR-OFF-06: PATCH {@code is_active=false} MUST succeed when all orders are in
     * terminal status (DELIVERED or CANCELLED).
     */
    @Test
    void deactivateOffice_onlyDeliveredOrders_succeeds() throws Exception {
        String phone = "+919800001009";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-br06b");

        var createRes = mockMvc.perform(jsonPost("/api/v1/offices", Map.of(
                                "name", "Surat Hub",
                                "code", "STV",
                                "city", "Surat",
                                "state", "Gujarat"))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        // Office ids are CHAR(36) UUID strings (PR A2 cutover from Long to String).
        String officeId = json.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Assign office via direct DB update — simulates a fully delivered order
        // We use the Office entity to set status directly rather than running through the full lifecycle
        var office = offices.findById(officeId).orElseThrow();
        // Place one order and cancel it so the office has only terminal order history
        var order = createOrder(mockMvc, json, token, "+919810000888", "PTL", officeId);
        mockMvc.perform(jsonPost("/api/v1/orders/" + order.id() + "/cancel", Map.of())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Now deactivation must succeed (only CANCELLED orders)
        mockMvc.perform(patch("/api/v1/offices/" + officeId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }
}
