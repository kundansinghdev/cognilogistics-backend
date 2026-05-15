package com.cognilogistic.user.repository;

import com.cognilogistic.user.model.PartnerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PartnerGroup}.
 */
@Repository
public interface PartnerGroupRepository extends JpaRepository<PartnerGroup, String> {

    /** All groups for a TP, including inactive ones. Ordered by name for stable UI. */
    List<PartnerGroup> findByTpAccountIdOrderByNameAsc(String tpAccountId);

    /** Lookup-by-name within a TP for the unique-name constraint check on create. */
    Optional<PartnerGroup> findByTpAccountIdAndName(String tpAccountId, String name);
}
