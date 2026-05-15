package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.TenderOrderRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TenderOrderRef} — the join table linking
 * PTL orders to tenders.
 */
public interface TenderOrderRefRepository extends JpaRepository<TenderOrderRef, String> {

    /**
     * Returns all order references linked to the given tender.
     *
     * @param tenderId the tender ID
     * @return all TenderOrderRef rows for that tender
     */
    List<TenderOrderRef> findByTenderId(String tenderId);
}
