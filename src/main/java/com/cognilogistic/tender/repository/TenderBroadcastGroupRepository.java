package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.TenderBroadcastGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TenderBroadcastGroup}.
 *
 * <p>R4 surface: lookup-by-tender for broadcast badge rendering ({@link #findByTenderId})
 * and lookup-by-group for partner-portal visibility checks ({@link #findByGroupId},
 * used in PR R7).
 */
@Repository
public interface TenderBroadcastGroupRepository extends JpaRepository<TenderBroadcastGroup, TenderBroadcastGroup.Pk> {

    /** All broadcast rows for a single tender — drives the "broadcast to N groups" badge. */
    List<TenderBroadcastGroup> findByTenderId(String tenderId);

    /** All tenders broadcast to a given group — drives the partner-portal visibility join. */
    List<TenderBroadcastGroup> findByGroupId(String groupId);
}
