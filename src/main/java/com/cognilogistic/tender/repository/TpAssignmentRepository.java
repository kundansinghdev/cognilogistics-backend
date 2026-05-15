package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.TpAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TpAssignment}.
 */
public interface TpAssignmentRepository extends JpaRepository<TpAssignment, String> {

    /**
     * Returns the assignment row for a tender, if one exists. Each tender produces at
     * most one assignment (the award handshake to the winning LP), so this returns
     * {@link Optional}.
     *
     * @param tenderId the tender's UUID
     * @return the assignment, if recorded
     */
    Optional<TpAssignment> findFirstByTenderId(String tenderId);

    /**
     * Returns every assignment a Logistics Partner has received. Used by the LP-side
     * "my won tenders" view.
     *
     * @param partnerId the partner profile id
     * @return list of assignments
     */
    List<TpAssignment> findByPartnerIdOrderBySentAtDesc(String partnerId);
}
