package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.Bid;
import com.cognilogistic.tender.model.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Bid}.
 */
public interface BidRepository extends JpaRepository<Bid, String> {

    /**
     * Returns every bid on a tender, in submission order. Used by the TP-side "review
     * bids" screen on the tender detail view.
     *
     * @param tenderId the tender's UUID
     * @return list of bids, oldest first
     */
    List<Bid> findByTenderIdOrderBySubmittedAtAsc(String tenderId);

    /**
     * Returns this LP's existing bid on a specific tender (if any). Used by the
     * submit-bid path to update-in-place rather than INSERT a duplicate row (the DB
     * UNIQUE on {@code (tender_id, partner_id)} would block the second INSERT anyway).
     *
     * @param tenderId  the tender's UUID
     * @param partnerId the Logistics Partner's profile id
     * @return the existing bid row, if one is present
     */
    Optional<Bid> findByTenderIdAndPartnerId(String tenderId, String partnerId);

    /**
     * Returns every bid placed by a specific Logistics Partner across all tenders.
     * Used by the LP-side "my bids" tab.
     *
     * @param partnerId the partner profile id
     * @return list of bids
     */
    List<Bid> findByPartnerIdOrderBySubmittedAtDesc(String partnerId);

    /**
     * Returns every PENDING bid on a tender. Used by the cancel-tender flow which
     * needs to flip these to REJECTED in bulk.
     *
     * @param tenderId the tender's UUID
     * @param status   typically {@link BidStatus#PENDING}
     * @return matching bids
     */
    List<Bid> findByTenderIdAndStatus(String tenderId, BidStatus status);
}
