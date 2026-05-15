package com.cognilogistic.order.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.order.dto.CustomerDto;
import com.cognilogistic.order.service.CustomerService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for customer lookup under {@code /api/v1/customers}.
 *
 * <p>{@code GET /lookup} requires a JWT and returns a customer row only when the phone
 * matches a customer whose {@code created_by_tp_id} equals the caller's TP account
 * (cross-tenant phone probing returns {@code data: null}).
 *
 * <p>{@code @Validated} is required here so the {@code @Pattern} on the {@code phone}
 * query parameter triggers constraint validation.
 */
@Tag(name = "Customers (lookup)", description = "TP customer lookup by phone query param. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/customers")
@Validated
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    /**
     * Looks up a customer by WhatsApp phone number.
     *
     * @param me    the authenticated TP user (correlation in logs only)
     * @param phone the customer's WhatsApp phone number (10–15 digits, optional leading +)
     * @return the customer record if found, or {@code null} (HTTP 200 with {@code data: null}) if not
     */
    @GetMapping("/lookup")
    public ApiResponse<CustomerDto> lookup(@CurrentUser AuthPrincipal me,
                                           @RequestParam("phone")
                                           @NotBlank
                                           @Pattern(regexp = "\\+?\\d{10,15}") String phone) {
        log.info("[ENTRY] customerLookup | userId={} | tp={} | phone={}",
                suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                ControllerRequestLogging.maskPhone(phone));
        return ControllerRequestLogging.withExitLog(CustomerController.class, "customerLookup", () ->
                customers.lookupForTp(phone, me != null ? me.tpAccountId() : null)
                        .map(c -> new CustomerDto(c.getId(), c.getWhatsappPhone(), c.getName(), c.isShadow()))
                        .orElse(null));
    }
}
