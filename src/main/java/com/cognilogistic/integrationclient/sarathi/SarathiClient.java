package com.cognilogistic.integrationclient.sarathi;

/**
 * Sarathi (driver licence) integration. Stubbed for UAT — concrete impl arrives post-UAT
 * alongside the fleet module rewrite. Do not depend on this in UAT-scope code.
 */
public interface SarathiClient {
    SarathiLookupResponse lookup(String licenceNumber);
}
