package com.cognilogistic.integrationclient.gst;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock implementation of {@link GstClient} that returns canned, deterministic responses
 * without contacting the GSTN registry. Active when {@code gst.mock=true} (the default,
 * ensuring local dev and UAT runs incur no real API cost).
 *
 * <p>Mirrors the {@code MockVahanClient} / {@code LoggingOtpProvider} / {@code MockSarathiClient}
 * pattern: a {@code @Service} bean gated on a property toggle, with a sibling Real
 * implementation activated by setting the toggle to {@code false} once the GSTN
 * sandbox / aggregator integration is wired (post-UAT).
 *
 * <p>The canned response is deliberately uniform — same legal name / trade name /
 * status regardless of the GSTIN supplied. Front-end tests that need varied data
 * can stub the SPI directly.
 */
@Service
@ConditionalOnProperty(name = "gst.mock", havingValue = "true", matchIfMissing = true)
public class MockGstClient implements GstClient {

    private static final Logger log = LoggerFactory.getLogger(MockGstClient.class);

    /**
     * Returns a canned response for any GSTIN. Logs the lookup so devs and QA can
     * verify the integration was exercised.
     *
     * @param gstin the GSTIN that was queried (echoed back in the response)
     * @return a deterministic mock {@link GstLookupResponse}
     */
    @Override
    public GstLookupResponse lookup(String gstin) {
        log.info("[MOCK GST] lookup gstin={}", gstin);
        // Canned shape: legal name, trade name, ACTIVE status. Front-end uses this to
        // autofill the company-create form during UAT demos.
        return new GstLookupResponse(
                gstin,
                "Mock Logistics Pvt Ltd",
                "Mock Logistics",
                "ACTIVE");
    }
}
