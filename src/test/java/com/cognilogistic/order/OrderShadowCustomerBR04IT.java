package com.cognilogistic.order;

import com.cognilogistic.auth.service.LoggingOtpProvider;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.support.AbstractIntegrationTest;
import com.cognilogistic.user.repository.OfficeRepository;
import com.cognilogistic.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.cognilogistic.support.OrderTestFixtures.createOffice;
import static com.cognilogistic.support.OrderTestFixtures.createOrder;
import static com.cognilogistic.support.TestFixtures.registerAndLogin;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-04: when the customer phone has no record, create a shadow customer synchronously
 * and link the order to it. Order creation must never fail because the customer doesn't exist.
 */
class OrderShadowCustomerBR04IT extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired LoggingOtpProvider otp;
    @Autowired UserRepository users;
    @Autowired OfficeRepository offices;
    @Autowired CustomerRepository customers;

    @Test
    void unknownPhone_createsShadowCustomer_andLinksOrder() throws Exception {
        String phone = "+919810000130";
        String token = registerAndLogin(mockMvc, json, otp, phone, "1234", "dev-A");
        String officeId = createOffice(offices, users, phone, "Pune-HQ");

        String customerPhone = "+919812345678";
        assertThat(customers.findByWhatsappPhone(customerPhone)).isEmpty();

        OrderDto o = createOrder(mockMvc, json, token, customerPhone, "PTL", officeId);

        var saved = customers.findByWhatsappPhone(customerPhone).orElseThrow();
        assertThat(saved.isShadow()).isTrue();
        assertThat(o.customerId()).isEqualTo(saved.getId());
    }
}
