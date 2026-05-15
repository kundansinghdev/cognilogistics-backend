package com.cognilogistic.tender.service;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.tender.dto.AssignTenderRequest;
import com.cognilogistic.tender.dto.PlaceBidRequest;
import com.cognilogistic.tender.dto.TenderDto;
import com.cognilogistic.tender.model.Bid;
import com.cognilogistic.tender.model.BidStatus;
import com.cognilogistic.tender.model.Tender;
import com.cognilogistic.tender.model.TenderAssignment;
import com.cognilogistic.tender.model.TenderStatus;
import com.cognilogistic.tender.repository.BidRepository;
import com.cognilogistic.tender.repository.TenderAssignmentRepository;
import com.cognilogistic.tender.repository.TenderRepository;
import com.cognilogistic.user.model.PartnerGroupMember;
import com.cognilogistic.user.repository.PartnerGroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partner-side tender service (BACKEND_GAPS §7b).
 *
 * <p>Three operations:
 * <ul>
 *   <li>{@link #listVisible} — tenders broadcast to a group containing the caller.</li>
 *   <li>{@link #placeBid} — upsert-style bid placement (replaces any prior PENDING
 *       bid by the same partner; the {@code (tender_id, partner_id)} UNIQUE makes
 *       this a row update rather than a duplicate insert).</li>
 *   <li>{@link #assign} — submit vehicle + driver after winning. 1:1 with the
 *       tender, locks once written.</li>
 * </ul>
 *
 * <p><strong>Authorisation</strong> — all methods call {@link #requirePartner}
 * which gates on {@link UserRole#PARTNER_TP} and a non-null
 * {@code partnerTpProfileId} on the principal. Cross-tenant tender access is
 * blocked by the visibility filter ({@link TenderRepository#findVisibleToGroups})
 * and explicit per-tender membership re-check on bid / assign.
 *
 * <p>The caller's {@link com.cognilogistic.tender.service.TenderService} hosts
 * the TP-side operations; this service is intentionally separate to keep the
 * tenancy boundary obvious in code review.
 */
@Service
public class PartnerTenderService {

    private final TenderRepository tenders;
    private final BidRepository bids;
    private final TenderAssignmentRepository assignments;
    private final PartnerGroupMemberRepository groupMembers;
    private final TenderService tenderService;

    public PartnerTenderService(TenderRepository tenders,
                                BidRepository bids,
                                TenderAssignmentRepository assignments,
                                PartnerGroupMemberRepository groupMembers,
                                TenderService tenderService) {
        this.tenders = tenders;
        this.bids = bids;
        this.assignments = assignments;
        this.groupMembers = groupMembers;
        this.tenderService = tenderService;
    }

    /**
     * Returns every tender visible to the calling partner — a tender is visible
     * iff at least one of the partner's groups appears in
     * {@code tender_broadcast_groups} for that tender.
     */
    @Transactional(readOnly = true)
    public List<TenderDto> listVisible(AuthPrincipal me) {
        requirePartner(me);
        List<String> myGroupIds = groupMembers.findByPartnerId(me.partnerTpProfileId()).stream()
                .map(PartnerGroupMember::getGroupId)
                .toList();
        if (myGroupIds.isEmpty()) {
            return List.of();
        }
        return tenders.findVisibleToGroups(myGroupIds).stream()
                .map(tenderService::buildPartnerView)
                .toList();
    }

    /**
     * Places (or revises) the calling partner's bid on a tender. Idempotent at
     * the {@code (tender, partner)} level — the row is upserted in place.
     *
     * <p>The bid's status is reset to {@link BidStatus#PENDING} on every call so
     * a partner can revise a previously-WITHDRAWN bid (or a REJECTED one if the
     * tender is re-broadcast). The TP's award flow flips PENDING → ACCEPTED for
     * the winner and PENDING → REJECTED for siblings.
     */
    @Transactional
    public TenderDto placeBid(AuthPrincipal me, String tenderId, PlaceBidRequest req) {
        requirePartner(me);
        Tender tender = requireVisible(me, tenderId);

        if (tender.getStatus() != TenderStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Bids only accepted while tender is IN_PROGRESS; current: " + tender.getStatus());
        }

        Bid bid = bids.findByTenderIdAndPartnerId(tenderId, me.partnerTpProfileId())
                .orElseGet(() -> {
                    Bid b = new Bid();
                    b.ensureId();
                    b.setTenderId(tenderId);
                    b.setPartnerId(me.partnerTpProfileId());
                    return b;
                });

        bid.setAmountInr(req.amountInr());
        bid.setEtaDays(req.etaDays());
        bid.setNotes(req.notes());
        bid.setStatus(BidStatus.PENDING);
        bids.save(bid);

        return tenderService.buildPartnerView(tender);
    }

    /**
     * Submits the awarded partner's vehicle + driver assignment. Constraints:
     * <ul>
     *   <li>Tender must be {@link TenderStatus#COMPLETED} — partner can't assign
     *       before winning.</li>
     *   <li>Tender's {@code awardedTo} must equal the caller's partner id —
     *       can't assign a tender that was awarded to a different partner.</li>
     *   <li>Tender must not already have an assignment row — locks once written.</li>
     * </ul>
     */
    @Transactional
    public TenderDto assign(AuthPrincipal me, String tenderId, AssignTenderRequest req) {
        requirePartner(me);
        Tender tender = requireVisible(me, tenderId);

        if (tender.getStatus() != TenderStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Tender must be COMPLETED before submitting assignment; current: " + tender.getStatus());
        }
        if (!me.partnerTpProfileId().equals(tender.getAwardedTo())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Tender was not awarded to your partner profile");
        }
        if (assignments.findById(tenderId).isPresent()) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Assignment already submitted for this tender — cannot re-submit");
        }

        TenderAssignment a = new TenderAssignment();
        a.setTenderId(tenderId);
        a.setVehicleNumber(req.vehicleNumber().toUpperCase());
        a.setDriverName(req.driverName());
        a.setDriverDl(req.driverDl() != null ? req.driverDl().toUpperCase() : null);
        a.setAssignedAt(Instant.now());
        a.setAssignedByUserId(me.userId());
        assignments.save(a);

        return tenderService.buildPartnerView(tender);
    }

    // ===== Helpers =====

    private void requirePartner(AuthPrincipal me) {
        if (me == null || me.role() != UserRole.PARTNER_TP) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only PARTNER_TP can call /partner endpoints");
        }
        if (me.partnerTpProfileId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User has no partner profile linkage");
        }
    }

    /**
     * Loads a tender and verifies it's visible to the calling partner — i.e. one
     * of the partner's groups is in the tender's broadcast set. Returns
     * {@code ORDER_NOT_FOUND} (rather than FORBIDDEN) on cross-tenant access to
     * avoid disclosing that the tender exists.
     */
    private Tender requireVisible(AuthPrincipal me, String tenderId) {
        Tender t = tenders.findById(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Tender not found"));
        List<String> myGroupIds = groupMembers.findByPartnerId(me.partnerTpProfileId()).stream()
                .map(PartnerGroupMember::getGroupId)
                .toList();
        if (myGroupIds.isEmpty()) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Tender not found");
        }
        // Any tender visible to this partner appears in findVisibleToGroups; the
        // explicit re-check protects against direct id access for ids the partner
        // never received.
        boolean visible = tenders.findVisibleToGroups(myGroupIds).stream()
                .anyMatch(tt -> tt.getId().equals(t.getId()));
        if (!visible) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Tender not found");
        }
        return t;
    }

    /** Suppresses an unused-import warning during compile churn. */
    @SuppressWarnings("unused")
    private static String unusedRef() { return Map.of("k", UUID.randomUUID()).toString(); }
}
