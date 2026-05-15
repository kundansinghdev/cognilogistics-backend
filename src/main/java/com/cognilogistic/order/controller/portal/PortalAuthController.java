package com.cognilogistic.order.controller.portal;

import com.cognilogistic.order.dto.portal.PortalSendOtpRequest;
import com.cognilogistic.order.dto.portal.PortalTokenResponse;
import com.cognilogistic.order.dto.portal.PortalVerifyOtpRequest;
import com.cognilogistic.order.service.portal.PortalAuthService;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for customer portal authentication under {@code /api/v1/portal/auth}.
 *
 * <p>The customer portal uses OTP-only authentication (no PIN). The flow is:
 * <ol>
 *   <li>POST /send-otp — sends a 6-digit OTP to the customer's WhatsApp phone</li>
 *   <li>POST /verify-otp — verifies the OTP and issues a short-lived CUSTOMER-role JWT</li>
 * </ol>
 *
 * <p>Only customers with {@code portal_access_enabled = true} can authenticate here;
 * access is granted by the TP account that originally created the customer.
 * This endpoint is intentionally separate from {@code /api/v1/auth} to keep
 * the two auth flows isolated.
 */
@Tag(name = "Auth (Customer portal)", description = "OTP-only customer JWT. Same routes under "
        + "`/api/v1/portal/auth/*` and `/api/v1/customer/auth/*`. No Bearer token required.")
@RestController
@RequestMapping(path = {"/api/v1/portal/auth", "/api/v1/customer/auth"})
public class PortalAuthController {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthController.class);

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    /**
     * Sends a 6-digit OTP to the customer's phone. Fails if no customer record exists
     * for that phone or if portal access has not been granted.
     *
     * @param req the send-OTP request containing the customer's phone number
     * @return empty success wrapper (the OTP is delivered out-of-band)
     */
    @PostMapping("/send-otp")
    public ApiResponse<java.util.Map<String, Object>> sendOtp(@Valid @RequestBody PortalSendOtpRequest req) {
        log.info("[ENTRY] portalSendOtp | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        return ControllerRequestLogging.withExitLog(PortalAuthController.class, "portalSendOtp", () -> {
            portalAuthService.sendOtp(req.phone());
            // Non-empty data so the FE's res.data.data is a real object, not undefined.
            return java.util.Map.of("sent", true);
        });
    }

    /**
     * Verifies the OTP and, on success, issues a CUSTOMER-role access token.
     * The token is returned directly (no refresh token — portal sessions are stateless and short-lived).
     *
     * @param req contains the phone and the 6-digit OTP entered by the customer
     * @return the portal access token to be used in subsequent portal API calls
     */
    @PostMapping("/verify-otp")
    public ApiResponse<PortalTokenResponse> verifyOtp(@Valid @RequestBody PortalVerifyOtpRequest req) {
        log.info("[ENTRY] portalVerifyOtp | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        return ControllerRequestLogging.withExitLog(PortalAuthController.class, "portalVerifyOtp", () -> {
            String token = portalAuthService.verifyOtp(req.phone(), req.otp());
            return new PortalTokenResponse(token);
        });
    }
}
