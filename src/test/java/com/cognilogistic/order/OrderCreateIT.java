package com.cognilogistic.order;

import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.readData;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CHANGE 1–3: order creation — officeId null, weightTons conversion, priceInr optional.
 */
class OrderCreateIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired CustomerRepository customers;

    private static Map<String, Object> baseCreateBody(String customerPhone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerWhatsappPhone", customerPhone);
        m.put("customerName", "Test Shipper");
        m.put("pickupLocation", "Pune");
        m.put("dropLocation", "Mumbai");
        m.put("pickupDate", "2026-06-01");
        m.put("goodsType", "General cargo");
        m.put("orderType", "PTL");
        m.put("express", false);
        return m;
    }

    @Test
    void createOrder_withNoOfficeId_succeeds_andAssignedOfficeIdIsNull() throws Exception {
        String phone = "+919810001001";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-c1");

        Map<String, Object> body = baseCreateBody("+919811001001");
        body.put("weightTons", 2.5);

        MvcResult res = mockMvc.perform(jsonPost("/api/v1/orders", body).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto dto = readData(json, res, new TypeReference<>() {});
        assertThat(dto.assignedOfficeId()).isNull();
        assertThat(dto.status().name()).isEqualTo("CREATED");
    }

    @Test
    void createOrder_withWeightTons2_5_storesWeightKg2500() throws Exception {
        String phone = "+919810001002";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-c2");

        Map<String, Object> body = baseCreateBody("+919811001002");
        body.put("weightTons", 2.5);

        MvcResult res = mockMvc.perform(jsonPost("/api/v1/orders", body).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto dto = readData(json, res, new TypeReference<>() {});
        // 2.5 T × 1000 = 2500 kg
        assertThat(dto.weightKg()).isEqualByComparingTo("2500");
    }

    @Test
    void createOrder_withNoPriceInr_succeeds_andPriceInrIsZero() throws Exception {
        String phone = "+919810001003";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-c3");

        Map<String, Object> body = baseCreateBody("+919811001003");

        MvcResult res = mockMvc.perform(jsonPost("/api/v1/orders", body).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto dto = readData(json, res, new TypeReference<>() {});
        // priceInr is now Integer (was BigDecimal). Schema column is INT NOT NULL DEFAULT 0,
        // so an omitted price falls through to a literal zero on the DTO.
        assertThat(dto.priceInr()).isZero();
    }

    /**
     * No {@code customerWhatsappPhone} and no {@code companyId} — service must still create
     * the order and a shadow {@code customers} row with {@code phone IS NULL} (Flyway
     * {@code V20260512001}).
     */
    @Test
    void createOrder_withoutCustomerPhone_createsPhonelessShadowCustomer() throws Exception {
        String phone = "+919810001099";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-c-no-phone");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerName", "No Phone Corp");
        body.put("pickupLocation", "Pune");
        body.put("dropLocation", "Mumbai");
        body.put("pickupDate", "2026-06-01");
        body.put("goodsType", "General cargo");
        body.put("orderType", "PTL");
        body.put("express", false);

        MvcResult res = mockMvc.perform(jsonPost("/api/v1/orders", body).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto dto = readData(json, res, new TypeReference<>() {});
        assertThat(dto.customerId()).isNotBlank();
        assertThat(dto.status().name()).isEqualTo("CREATED");

        var customer = customers.findById(dto.customerId()).orElseThrow();
        assertThat(customer.getWhatsappPhone()).isNull();
        assertThat(customer.isShadow()).isTrue();
        assertThat(customer.getName()).isEqualTo("No Phone Corp");
    }
}
