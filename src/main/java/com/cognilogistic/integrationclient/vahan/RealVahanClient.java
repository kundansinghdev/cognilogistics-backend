package com.cognilogistic.integrationclient.vahan;

import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Production implementation of {@link VahanClient} that calls the real VAHAN API.
 * Active only when {@code vahan.mock=false}.
 *
 * <p>Configured via two application properties:
 * <ul>
 *   <li>{@code vahan.base-url} — the base URL of the VAHAN API endpoint</li>
 *   <li>{@code vahan.api-key} — the API key sent in the {@code X-Api-Key} header</li>
 * </ul>
 *
 * <p>Any HTTP or network error is wrapped in an {@link com.cognilogistic.platform.api.ApiException}
 * with error code {@link com.cognilogistic.platform.api.ErrorCode#VAHAN_UNAVAILABLE}.
 */
@Service
@ConditionalOnProperty(name = "vahan.mock", havingValue = "false")
public class RealVahanClient implements VahanClient {

    private static final Logger log = LoggerFactory.getLogger(RealVahanClient.class);

    private final RestClient client;

    public RealVahanClient(@Value("${vahan.base-url}") String baseUrl,
                           @Value("${vahan.api-key:}") String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    /**
     * Calls the VAHAN API to look up vehicle details for the given registration number.
     *
     * @param reg the Indian vehicle registration number (e.g., "MH12AB1234")
     * @return vehicle details from the VAHAN registry
     * @throws com.cognilogistic.platform.api.ApiException with VAHAN_UNAVAILABLE if the API call fails
     */
    @Override
    public VahanLookupResponse lookup(String reg) {
        try {
            return client.get()
                    .uri("/vehicle/{reg}", reg)
                    .retrieve()
                    .body(VahanLookupResponse.class);
        } catch (Exception e) {
            log.warn("Vahan API call failed for {}", reg, e);
            throw new ApiException(ErrorCode.VAHAN_UNAVAILABLE, "Vahan API call failed: " + e.getMessage());
        }
    }
}
