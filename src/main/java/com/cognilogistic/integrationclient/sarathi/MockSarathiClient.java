package com.cognilogistic.integrationclient.sarathi;

import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Smart mock Sarathi client. Mirrors {@link com.cognilogistic.integrationclient.vahan.MockVahanClient}'s
 * prefix-based smart-mock convention: prefix {@code WW} = warning, {@code NN} = not found, anything
 * else returns plausible data. Active when {@code sarathi.mock=true} (the default).
 */
@Service
@ConditionalOnProperty(name = "sarathi.mock", havingValue = "true", matchIfMissing = true)
public class MockSarathiClient implements SarathiClient {

    @Override
    public SarathiLookupResponse lookup(String dl) {
        if (dl == null || dl.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Driver licence number is required");
        }
        if (dl.startsWith("NN")) {
            throw new ApiException(ErrorCode.SARATHI_UNAVAILABLE, "Driver licence not found in Sarathi registry");
        }
        if (dl.startsWith("WW")) {
            return new SarathiLookupResponse(
                    dl, "Test Driver", "Test Parent", "1985-08-12",
                    List.of("LMV", "HMV"), "ACTIVE",
                    "2020-01-01", "2026-06-30",
                    "Expiring within 60 days");
        }
        return new SarathiLookupResponse(
                dl, "Mock Driver", "Mock Parent", "1985-08-12",
                List.of("LMV", "HMV"), "ACTIVE",
                "2020-01-01", "2030-12-31", null);
    }
}
