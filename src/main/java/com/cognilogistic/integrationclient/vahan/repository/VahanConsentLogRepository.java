package com.cognilogistic.integrationclient.vahan.repository;

import com.cognilogistic.integrationclient.vahan.model.VahanConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link com.cognilogistic.integrationclient.vahan.model.VahanConsentLog}.
 *
 * <p>Provides queries against the {@code vahan_consent_log} table to enforce BR-10
 * (Vahan consent must exist before fleet confirmation for FTL orders).
 */
public interface VahanConsentLogRepository extends JpaRepository<VahanConsentLog, String> {

    /**
     * Returns all consent log rows for a given order+vehicle pair, useful for audit.
     *
     * @param orderId             the order ID
     * @param vehicleRegistration the vehicle registration number
     * @return list of consent rows (may include both accepted and declined records)
     */
    List<VahanConsentLog> findByOrderIdAndVehicleRegistration(String orderId, String vehicleRegistration);

    /**
     * Returns the most recent consent log entry for a given order+vehicle pair.
     * Used by {@code VahanService.requireVahanConsent} to check the latest consent decision.
     *
     * @param orderId             the order ID
     * @param vehicleRegistration the vehicle registration number
     * @return the most recently created consent row, or empty if no row exists
     */
    Optional<VahanConsentLog> findTopByOrderIdAndVehicleRegistrationOrderByCreatedAtDesc(String orderId, String vehicleRegistration);
}
