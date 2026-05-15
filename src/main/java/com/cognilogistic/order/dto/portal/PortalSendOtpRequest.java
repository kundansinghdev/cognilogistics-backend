package com.cognilogistic.order.dto.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/portal/auth/send-otp}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — the customer's registered WhatsApp phone number;
 *       must match an existing customer record with portal access enabled</li>
 * </ul>
 */
public record PortalSendOtpRequest(
        @NotBlank
        @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10–15 digits, optional leading +")
        String phone
) {}
