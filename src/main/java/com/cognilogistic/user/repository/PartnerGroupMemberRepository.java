package com.cognilogistic.user.repository;

import com.cognilogistic.user.model.PartnerGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PartnerGroupMember}.
 */
@Repository
public interface PartnerGroupMemberRepository extends JpaRepository<PartnerGroupMember, PartnerGroupMember.Pk> {

    /** All members of a single group — drives the FE's group-detail card. */
    List<PartnerGroupMember> findByGroupId(String groupId);

    /** All groups containing a partner — drives the partner-portal visibility join (R7). */
    List<PartnerGroupMember> findByPartnerId(String partnerId);

    /**
     * Bulk-deletes the membership rows for a group. Used by {@code PATCH /partner-groups/{id}}
     * which receives the full intended {@code partnerIds} list and replaces the membership in place.
     */
    @Transactional
    void deleteByGroupId(String groupId);
}
