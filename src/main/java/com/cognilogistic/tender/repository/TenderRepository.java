package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.Tender;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Tender} entities.
 *
 * <p>Provides standard CRUD operations. Query methods will be added post-UAT
 * when the bid and assignment lifecycle is implemented.
 */
public interface TenderRepository extends JpaRepository<Tender, String> {

    /** All tenders for a single TP, newest first — list endpoint hot path. */
    java.util.List<Tender> findByTpAccountIdOrderByCreatedAtDesc(String tpAccountId);

    /**
     * Counts existing tenders for a TP whose {@code tender_number} starts with the
     * given prefix. Used by the tender-number generator to pick the next sequence
     * number for the day, mirroring the order-number pattern in {@code OrderRepository}.
     */
    long countByTpAccountIdAndTenderNumberStartingWith(String tpAccountId, String tenderNumberPrefix);

    /**
     * Returns the tenders that have been broadcast to at least one of the supplied
     * partner-group ids. Drives the partner-portal visibility filter
     * (BACKEND_GAPS §7b) — a partner sees a tender iff one of their groups appears
     * in {@code tender_broadcast_groups} for that tender.
     *
     * <p>Caller should pass an empty list to get an empty result without an extra
     * round-trip.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT t FROM Tender t
             WHERE t.id IN (
                 SELECT tbg.tenderId FROM TenderBroadcastGroup tbg
                  WHERE tbg.groupId IN :groupIds
             )
             ORDER BY t.createdAt DESC
            """)
    java.util.List<Tender> findVisibleToGroups(
            @org.springframework.data.repository.query.Param("groupIds") java.util.List<String> groupIds);
}
