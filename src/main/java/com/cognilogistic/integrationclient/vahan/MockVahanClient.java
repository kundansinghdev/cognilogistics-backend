package com.cognilogistic.integrationclient.vahan;

import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Smart mock per HTML spec: prefix WW = warning, prefix NN = not found, anything else = plausible data.
 */
@Service
@ConditionalOnProperty(name = "vahan.mock", havingValue = "true", matchIfMissing = true)
public class MockVahanClient implements VahanClient {

    @Override
    public VahanLookupResponse lookup(String reg) {
        if (reg == null || reg.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Vehicle registration is required");
        }
        if (reg.startsWith("NN")) {
            throw new ApiException(ErrorCode.VAHAN_UNAVAILABLE, "Vehicle not found in Vahan registry");
        }
        if (reg.startsWith("WW")) {
            return new VahanLookupResponse(reg, "Test Owner", "TATA / Ace", "DIESEL",
                    "2020-04-12", "2027-04-11", "2026-04-11", "ACTIVE",
                    "Insurance expiring within 90 days");
        }
        return new VahanLookupResponse(reg, "Mock Owner", "Ashok Leyland / Dost", "DIESEL",
                "2022-08-01", "2030-07-31", "2026-08-01", "ACTIVE", null);
    }
}
