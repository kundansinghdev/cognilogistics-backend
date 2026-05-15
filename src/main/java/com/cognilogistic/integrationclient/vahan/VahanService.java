package com.cognilogistic.integrationclient.vahan;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.integrationclient.vahan.model.VahanConsentLog;
import com.cognilogistic.integrationclient.vahan.repository.VahanConsentLogRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.order.service.OrderService;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates Vahan (MoRTH vehicle registry) consent recording
 * and lookup for FTL fleet confirmation (BR-09, BR-10).
 *
 * <p>Implements {@link OrderService.FleetConfirmGuard} so {@code OrderService} can call
 * {@link #requireVahanConsent} at FLEET_CONFIRMED time without creating an upward dependency
 * from the order module into the integration-client module.
 *
 * <p>All lookup and consent operations scope-check the order to the requesting TP account
 * to prevent cross-tenant data leakage.
 */
@Service
public class VahanService implements OrderService.FleetConfirmGuard {

    private static final Logger log = LoggerFactory.getLogger(VahanService.class);

    /** Last 4 chars of an id; matches the masking convention used elsewhere in the code. */
    private static String suffix(String id) {
        if (id == null || id.length() < 4) return "****";
        return "****" + id.substring(id.length() - 4);
    }

    private final VahanConsentLogRepository consentLog;
    private final VahanClient client;
    private final OrderRepository orders;
    private final boolean mockMode;

    public VahanService(VahanConsentLogRepository consentLog,
                        VahanClient client,
                        OrderRepository orders,
                        @Value("${vahan.mock:true}") boolean mockMode) {
        this.consentLog = consentLog;
        this.client = client;
        this.orders = orders;
        this.mockMode = mockMode;
    }

    /**
     * Records the TP user's Vahan consent decision for a specific vehicle+order combination.
     * The caller must belong to the TP account that owns the order (tenant isolation).
     *
     * @param me                  the authenticated TP user recording consent
     * @param orderId             the order for which consent is being recorded
     * @param vehicleRegistration the vehicle registration number
     * @param consentText         the full text of the consent statement shown to the user
     * @param consentGiven        {@code true} if the user accepted, {@code false} if they declined
     * @throws com.cognilogistic.platform.api.ApiException with ORDER_NOT_FOUND if the order
     *         does not belong to the caller's TP account
     */
    @Transactional
    public void recordConsent(AuthPrincipal me, String orderId, String vehicleRegistration,
                              String consentText, boolean consentGiven) {
        // Order must belong to this TP — ensures users can't write consent rows for other TPs.
        // The tenant-scope check stays IN even though we're not persisting; it gives the
        // FE a meaningful 404 / 200 response and the audit trail of "request was authorised".
        orders.findById(orderId)
                .filter(o -> o.getTpAccountId().equals(me.tpAccountId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order " + orderId + " not found"));

        // ─────────────────────────────────────────────────────────────────────
        // TODO(sarathi-vahan-real-api): Vahan consent persistence is
        //   intentionally disabled until the real Vahan integration lands.
        //   Until then we have nothing to validate consent FOR, so storing
        //   consent rows would just accumulate unverifiable data.
        //
        //   Endpoint still validates tenant scope + returns 200 / {recorded:true}
        //   so the FE flow is unblocked. When the real Vahan integration ships,
        //   uncomment the block below — schema (vahan_consent_log) is unchanged
        //   and ready.
        //   Confirmed by user 2026-05-10 (Option A in BACKEND_GAPS thread).
        // ─────────────────────────────────────────────────────────────────────
        // VahanConsentLog row = new VahanConsentLog();
        // row.setOrderId(orderId);
        // row.setUserId(me.userId());
        // row.setVehicleRegistration(vehicleRegistration);
        // row.setConsentText(consentText);
        // row.setConsentGiven(consentGiven);
        // row.setMockMode(mockMode);
        // consentLog.save(row);

        // Audit-level trace so we still have a forensic record of the consent attempt
        // even though no row is being written. Masked ids; never log the consentText
        // verbatim (could contain free-form user input).
        log.info("Vahan consent acknowledged (persistence paused) | orderId={} | userId={} | tp={} | vehiclePresent={} | consentGiven={}",
                suffix(orderId), suffix(me.userId()), suffix(me.tpAccountId()),
                vehicleRegistration != null && !vehicleRegistration.isBlank(), consentGiven);
    }

    /**
     * Performs a Vahan registry lookup for the given vehicle, enforcing that consent has been
     * recorded first (BR-10). Also enforces tenant isolation by checking that the order belongs
     * to the requesting TP account.
     *
     * @param me                  the authenticated TP user
     * @param orderId             the order context for the lookup
     * @param vehicleRegistration the vehicle registration to query
     * @return the Vahan registry data for the vehicle
     * @throws com.cognilogistic.platform.api.ApiException with INTEGRATION_CONSENT_REQUIRED if no
     *         positive consent log row exists for this order+vehicle combination
     */
    @Transactional(readOnly = true)
    public VahanLookupResponse lookup(AuthPrincipal me, String orderId, String vehicleRegistration) {
        orders.findById(orderId)
                .filter(o -> o.getTpAccountId().equals(me.tpAccountId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order " + orderId + " not found"));
        // BR-10: consent log row must exist for this order + vehicle.
        requireVahanConsent(orderId, vehicleRegistration);
        return client.lookup(vehicleRegistration);
    }

    /**
     * Validates that a positive Vahan consent log row exists for the given order+vehicle pair.
     * Called by {@link OrderService#confirmFleet} to enforce BR-10 before changing the order
     * status to FLEET_CONFIRMED.
     *
     * @param orderId             the order being confirmed
     * @param vehicleRegistration the vehicle to be used for the order
     * @throws com.cognilogistic.platform.api.ApiException with INTEGRATION_CONSENT_REQUIRED if no
     *         positive consent row is found
     */
    @Override
    public void requireVahanConsent(String orderId, String vehicleRegistration) {
        consentLog.findTopByOrderIdAndVehicleRegistrationOrderByCreatedAtDesc(orderId, vehicleRegistration)
                .filter(VahanConsentLog::isConsentGiven)
                .orElseThrow(() -> new ApiException(ErrorCode.INTEGRATION_CONSENT_REQUIRED,
                        "Vahan consent must be recorded before fleet confirmation"));
    }
}
