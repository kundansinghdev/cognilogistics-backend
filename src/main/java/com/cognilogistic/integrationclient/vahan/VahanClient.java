package com.cognilogistic.integrationclient.vahan;

public interface VahanClient {

    /**
     * Look up vehicle details for an Indian registration number.
     * Implementations selected by `vahan.mock` property.
     */
    VahanLookupResponse lookup(String vehicleRegistration);
}
