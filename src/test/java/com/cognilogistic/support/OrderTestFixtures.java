package com.cognilogistic.support;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.user.model.Office;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.readData;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order/office helpers for integration tests. Encapsulates the repetitive
 * "create office, create order, transition to X" sequence.
 *
 * <p><strong>2026-05-08 — id types are {@link String} UUIDs.</strong> The PR-A2
 * cutover replaced {@code Long} primary keys with CHAR(36) UUID strings across
 * every entity. The fixture signatures here mirror that.
 */
public final class OrderTestFixtures {

    private OrderTestFixtures() {}

    /**
     * Saves a new {@link Office} for the TP account that owns the user with the given phone, and
     * returns the generated office id (CHAR(36) UUID). Defaults injected by the fixture:
     * {@code city=Pune}, {@code state=MH}, {@code pincode=411001}, {@code active=true}, and a
     * derived 1–10 char uppercase {@code code} (BR-OFF-01) made by stripping non-alphanumerics
     * from {@code name}.
     *
     * <p>Side effects: writes one row to {@code offices} via {@code repo.save}. Not transactional
     * — the row <b>commits</b> and survives in the shared MySQL container until the JVM exits.
     */
    public static String createOffice(OfficeRepository repo, UserRepository users, String phone, String name) {
        String tpId = users.findByPhone(phone).orElseThrow().getTpAccountId();
        Office o = new Office();
        // Office uses explicit ensureId() rather than @GeneratedValue (PR A2 convention) so we
        // populate the id ourselves before save() — keeps the inserted UUID predictable for tests.
        o.ensureId();
        o.setTpAccountId(tpId);
        o.setName(name);
        // code is required (BR-OFF-01); derive a short code from the name for test fixture convenience
        o.setCode(name.replaceAll("[^A-Z0-9]", "").toUpperCase().substring(0, Math.min(10, name.replaceAll("[^A-Z0-9]", "").length())));
        o.setCity("Pune");
        o.setState("MH");
        o.setPincode("411001");
        o.setActive(true);
        repo.save(o);
        return o.getId();
    }

    /**
     * Posts a {@code POST /api/v1/orders} call with the supplied access token, asserts HTTP 200,
     * and returns the parsed {@link OrderDto}. Defaults injected by the fixture:
     * {@code materialDescription="Cement bags"}, {@code weightTons=1.5} (stored as 1500 kg),
     * {@code priceInr=12500}, {@code express=false}.
     *
     * <p>Note: V3.6 no longer sends {@code office_id} on create — the {@code officeId}
     * parameter is only kept for caller convenience and is not put on the wire. Callers do
     * the assign in a separate {@code /acknowledge} call.
     *
     * <p>Side effects: writes one row to {@code orders}, one to {@code order_status_log}
     * (BR-06 initial CREATED), and possibly one to {@code customers} (BR-04 shadow customer).
     * Not transactional — these inserts <b>commit</b> against the shared container.
     */
    public static OrderDto createOrder(MockMvc mvc, ObjectMapper json, String accessToken,
                                       String customerPhone, String orderType,
                                       String officeId) throws Exception {
        // officeId param kept for caller convenience (used in separate assign/acknowledge steps).
        // It is no longer sent in the create body — office is set via POST /orders/{id}/acknowledge.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerWhatsappPhone", customerPhone);
        body.put("customerName", "Fixture Customer");
        body.put("pickupLocation", "Pune");
        body.put("dropLocation", "Mumbai");
        body.put("pickupDate", "2026-06-10");
        body.put("goodsType", "Cement bags");
        body.put("orderType", orderType);
        body.put("weightTons", 1.5);   // 1.5 T = 1500 kg stored
        body.put("priceInr", 12500);
        body.put("express", false);

        MvcResult res = mvc.perform(jsonPost("/api/v1/orders", body).header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return readData(json, res, new TypeReference<>() {});
    }

    /**
     * Posts a {@code POST /api/v1/orders/{orderId}/{action}} request with an empty JSON body
     * ({@code {}}) and the supplied access token, asserts HTTP 200, and returns the parsed
     * {@link OrderDto}. Useful for transitions that take no payload, e.g. {@code start-transit},
     * {@code deliver}. For {@code acknowledge} on an order with no office yet, the empty body
     * will fail validation — call the controller directly with an {@code officeId}. For
     * {@code confirm-fleet} use the {@link #transition(MockMvc, ObjectMapper, String, String, String, Object)}
     * overload to pass {@code driverName} (and {@code vehicleRegistration} for FTL).
     *
     * <p>Side effects: writes one row to {@code order_status_log} for every successful
     * transition (BR-06). Not transactional — these inserts <b>commit</b>.
     */
    public static OrderDto transition(MockMvc mvc, ObjectMapper json, String accessToken,
                                      String orderId, String action) throws Exception {
        return transition(mvc, json, accessToken, orderId, action, Map.of());
    }

    /**
     * Same as the no-body overload, but sends the supplied {@code body} as the JSON request
     * payload. Use this when the transition needs fields — currently {@code confirm-fleet}
     * requires {@code driverName} and (for FTL) {@code vehicleRegistration}.
     *
     * <p>Side effects: writes one row to {@code order_status_log} for every successful
     * transition (BR-06). Not transactional — these inserts <b>commit</b>.
     */
    public static OrderDto transition(MockMvc mvc, ObjectMapper json, String accessToken,
                                      String orderId, String action, Object body) throws Exception {
        MvcResult res = mvc.perform(jsonPost("/api/v1/orders/" + orderId + "/" + action, body)
                        .header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return readData(json, res, new TypeReference<>() {});
    }
}
