package com.cognilogistic.integrationclient.gst.repository;

import com.cognilogistic.integrationclient.gst.model.GstConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link GstConsentLog}.
 */
public interface GstConsentLogRepository extends JpaRepository<GstConsentLog, String> {

    /**
     * Returns the most recent consent row for a (GSTIN, user) pair. Used as a freshness
     * check before a GST lookup — see {@link com.cognilogistic.integrationclient.sarathi.repository.SarathiConsentLogRepository}
     * Javadoc for the time-bound-consent rationale.
     *
     * @param gstin  the GSTIN being looked up
     * @param userId the user issuing the lookup
     * @return the most recent matching row, if any
     */
    Optional<GstConsentLog> findFirstByGstinAndUserIdOrderByConsentAtDesc(
            String gstin, String userId);
}
