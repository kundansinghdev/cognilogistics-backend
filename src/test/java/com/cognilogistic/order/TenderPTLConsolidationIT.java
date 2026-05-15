package com.cognilogistic.order;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.tender.repository.TenderOrderRefRepository;
import com.cognilogistic.user.repository.OfficeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static com.cognilogistic.support.OrderTestFixtures.createOffice;
import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.TestFixtures.jsonPost;
import static com.cognilogistic.support.TestFixtures.readData;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHANGE 6: PTL order consolidation into a tender.
 */
class TenderPTLConsolidationIT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;
    @Autowired TenderOrderRefRepository tenderOrderRefRepo;

    @Test
    void createTender_withPtlOrderIds_setsWeightAndInsertsRefs() throws Exception {
        String phone = "+919810004001";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-t1");
        String officeId = createOffice(offices, users, phone, "Bengaluru-HQ");

        // Create two PTL orders and acknowledge each (1.5 T each = 1500 kg each)
        OrderDto o1 = createOrder(mockMvc, json, token, "+919811004001", "PTL", officeId);
        OrderDto o2 = createOrder(mockMvc, json, token, "+919811004002", "PTL", officeId);

        for (OrderDto o : List.of(o1, o2)) {
            mockMvc.perform(jsonPost("/api/v1/orders/" + o.id() + "/acknowledge",
                            Map.of("officeId", officeId))
                            .header(AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        // Create tender with both order IDs
        MvcResult res = mockMvc.perform(jsonPost("/api/v1/tenders",
                        Map.of(
                                "description", "Test consolidation tender",
                                "orderIds", List.of(o1.id(), o2.id())
                        )).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // weight_kg should be sum: 1500 + 1500 = 3000
        Map<String, Object> data = readData(json, res, new TypeReference<>() {});
        assertThat(((Number) data.get("weightKg")).intValue()).isEqualTo(3000);

        // tender_order_refs rows should be inserted. Tender id is now a CHAR(36)
        // UUID string (PR A2 cutover from Long), so the JSON value comes back as a
        // text node rather than a numeric one — read with toString().
        String tenderId = data.get("id").toString();
        assertThat(tenderOrderRefRepo.findByTenderId(tenderId)).hasSize(2);
    }

    @Test
    void createTender_withFtlOrderId_returns400ValidationError() throws Exception {
        String phone = "+919810004002";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-t2");
        String officeId = createOffice(offices, users, phone, "Kolkata-HQ");

        // Create an FTL order and acknowledge it
        OrderDto ftlOrder = createOrder(mockMvc, json, token, "+919811004003", "FTL", officeId);
        mockMvc.perform(jsonPost("/api/v1/orders/" + ftlOrder.id() + "/acknowledge",
                        Map.of("officeId", officeId))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Including an FTL order in tender orderIds → 400
        mockMvc.perform(jsonPost("/api/v1/tenders",
                        Map.of(
                                "description", "Invalid tender",
                                "orderIds", List.of(ftlOrder.id())
                        )).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
