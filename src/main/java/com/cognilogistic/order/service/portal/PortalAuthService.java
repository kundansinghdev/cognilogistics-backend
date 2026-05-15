package com.cognilogistic.order.service.portal;

import com.cognilogistic.auth.model.OtpPurpose;
import com.cognilogistic.auth.service.OtpService;
import com.cognilogistic.auth.service.TokenService;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling OTP-based authentication for the customer portal.
 *
 * <p>Unlike the TP auth flow (phone + PIN), the portal uses OTP-only authentication.
 * Only customers with {@code portal_access_enabled = true} can authenticate.
 * On successful OTP verification, a short-lived CUSTOMER-role JWT is issued
 * (no refresh token — the customer re-authenticates via OTP when it expires).
 */
@Service
public class PortalAuthService {

    private final CustomerRepository customers;
    private final OtpService otpService;
    private final TokenService tokenService;

    public PortalAuthService(CustomerRepository customers, OtpService otpService, TokenService tokenService) {
        this.customers = customers;
        this.otpService = otpService;
        this.tokenService = tokenService;
    }

    /**
     * Sends a 6-digit OTP to the customer's phone for portal login.
     * Fails with {@code PORTAL_ACCESS_DENIED} if no customer record exists or
     * if portal access has not been granted by the TP account.
     *
     * @param phone the customer's registered WhatsApp phone number
     */
    @Transactional
    public void sendOtp(String phone) {
        Customer customer = customers.findByWhatsappPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.PORTAL_ACCESS_DENIED,
                        "No customer account found for this phone number"));
        if (!customer.isPortalAccessEnabled()) {
            throw new ApiException(ErrorCode.PORTAL_ACCESS_DENIED,
                    "Portal access is not enabled for this account. Contact your logistics provider.");
        }
        otpService.send(phone, OtpPurpose.PORTAL_LOGIN);
    }

    /**
     * Verifies the OTP and, on success, issues a CUSTOMER-role access token.
     * Re-checks portal access after OTP verification in case access was revoked between
     * the send and verify steps.
     *
     * @param phone the customer's phone number
     * @param otp   the 6-digit OTP entered by the customer
     * @return a signed JWT with CUSTOMER role, to be used as a Bearer token
     */
    @Transactional
    public String verifyOtp(String phone, String otp) {
        otpService.verify(phone, OtpPurpose.PORTAL_LOGIN, otp);
        Customer customer = customers.findByWhatsappPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.PORTAL_ACCESS_DENIED,
                        "Customer not found after OTP verification"));
        if (!customer.isPortalAccessEnabled()) {
            throw new ApiException(ErrorCode.PORTAL_ACCESS_DENIED,
                    "Portal access is not enabled for this account");
        }
        return tokenService.issuePortalAccessToken(customer);
    }
}
