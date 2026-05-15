package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.PartnerTpProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PartnerTpProfile}.
 *
 * <p>R4 surface: just the standard {@code findById} (used to populate
 * {@code partnerName} on bid responses) plus {@link #findByUserId}. PR R5
 * extends this with listing / search / network membership queries.
 */
@Repository
public interface PartnerTpProfileRepository extends JpaRepository<PartnerTpProfile, String> {

    /**
     * Locates a partner profile by its owning {@code user_id}. UNIQUE on the schema
     * — at most one profile per user.
     */
    Optional<PartnerTpProfile> findByUserId(String userId);
}
