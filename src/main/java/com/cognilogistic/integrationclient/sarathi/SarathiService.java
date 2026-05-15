package com.cognilogistic.integrationclient.sarathi;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.integrationclient.sarathi.model.SarathiConsentLog;
import com.cognilogistic.integrationclient.sarathi.repository.SarathiConsentLogRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates Sarathi (driver-licence registry) consent
 * recording and lookups (BACKEND_GAPS §12.2).
 *
 * <p>Mirrors {@link com.cognilogistic.integrationclient.vahan.VahanService}'s
 * pattern so the two integrations behave identically from the FE's perspective.
 *
 * <p><strong>Consent gate:</strong> {@link #lookup} requires a positive consent
 * row to exist for the order + DL combination. The driver-DL field on confirm-fleet
 * is advisory in R8 — the FE-driven flow records consent silently on the
 * "Run Sarthi check" button click, and the order-level gate stays relaxed via
 * {@code orders.fleet.require-vahan-consent} (which serves both Vahan and
 * Sarathi for UAT) so demos work end-to-end.
 */
@Service
public class SarathiService {

    private static final Logger log = LoggerFactory.getLogger(SarathiService.class);

    /** Last 4 chars of an id; matches the masking convention used elsewhere in the code. */
    private static String suffix(String id) {
        if (id == null || id.length() < 4) return "****";
        return "****" + id.substring(id.length() - 4);
    }

    private final SarathiConsentLogRepository consentLog;
    private final SarathiClient client;
    private final OrderRepository orders;
    private final boolean mockMode;

    public SarathiService(SarathiConsentLogRepository consentLog,
                          SarathiClient client,
                          OrderRepository orders,
                          @Value("${sarathi.mock:true}") boolean mockMode) {
        this.consentLog = consentLog;
        this.client = client;
        this.orders = orders;
        this.mockMode = mockMode;
    }

    /**
     * Records the TP user's Sarathi consent decision for a specific order + DL pair.
     * Tenant-scoped — the order must belong to the caller's TP. DL is uppercased
     * before save (matches the column convention used everywhere else).
     */
    @Transactional
    public void recordConsent(AuthPrincipal me, String orderId, String driverDl,
                              String consentText, boolean consentGiven) {
        // Tenant-scope check stays in even though we're not persisting, so the FE
        // gets a meaningful 404 if it tries to record consent for someone else's order.
        orders.findById(orderId)
                .filter(o -> o.getTpAccountId().equals(me.tpAccountId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND,
                        "Order " + orderId + " not found"));

        // Decline still surfaces a 422 — the FE distinguishes "user declined"
        // from "consent recorded" off this. Persistence happens regardless;
        // we just don't store anything on the accept side either.
        if (!consentGiven) {
            throw new ApiException(ErrorCode.INTEGRATION_CONSENT_REQUIRED,
                    "Consent declined; lookup not authorised");
        }

        // ─────────────────────────────────────────────────────────────────────
        // TODO(sarathi-vahan-real-api): Sarathi consent persistence is
        //   intentionally disabled until the real Sarathi integration lands.
        //   Storing consent rows for unverifiable lookups would just accumulate
        //   noise. Endpoint still validates tenant scope + decline path, then
        //   returns 200 / {recorded:true}. Schema (sarathi_consent_log) is
        //   unchanged and ready for re-enablement.
        //   Confirmed by user 2026-05-10 (Option A in BACKEND_GAPS thread).
        // ─────────────────────────────────────────────────────────────────────
        // SarathiConsentLog row = new SarathiConsentLog();
        // row.ensureId();
        // row.setOrderId(orderId);
        // row.setUserId(me.userId());
        // row.setDlNumber(driverDl == null ? null : driverDl.toUpperCase());
        // consentLog.save(row);

        // Audit-level trace: we acknowledged consent without persisting, so the only
        // forensic record is this log line. Masked ids only — no DL leaked.
        log.info("Sarathi consent acknowledged (persistence paused) | orderId={} | userId={} | tp={} | dlPresent={}",
                suffix(orderId), suffix(me.userId()), suffix(me.tpAccountId()),
                driverDl != null && !driverDl.isBlank());
    }

    /**
     * Looks up a driver licence in the Sarathi registry. Requires a positive
     * consent row for this order + DL pair (BR-FLT-04).
     */
    @Transactional(readOnly = true)
    public SarathiLookupResponse lookup(AuthPrincipal me, String orderId, String driverDl) {
        orders.findById(orderId)
                .filter(o -> o.getTpAccountId().equals(me.tpAccountId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND,
                        "Order " + orderId + " not found"));

        // Consent gate — at least one row for (orderId, dl) must exist. Uses an indexed
        // derived query rather than the previous findAll()+stream() scan, which would
        // degrade linearly with consent-log volume once persistence is re-enabled.
        String normalisedDl = driverDl == null ? null : driverDl.toUpperCase();
        boolean hasConsent = normalisedDl != null
                && consentLog.existsByOrderIdAndDlNumber(orderId, normalisedDl);
        if (!hasConsent) {
            throw new ApiException(ErrorCode.INTEGRATION_CONSENT_REQUIRED,
                    "Sarathi consent must be recorded before lookup");
        }
        return client.lookup(normalisedDl);
    }
}
