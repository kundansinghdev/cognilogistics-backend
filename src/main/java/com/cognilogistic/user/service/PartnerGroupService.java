package com.cognilogistic.user.service;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.tender.repository.TenderBroadcastGroupRepository;
import com.cognilogistic.user.dto.PartnerGroupDto;
import com.cognilogistic.user.dto.PartnerGroupRequest;
import com.cognilogistic.user.model.PartnerGroup;
import com.cognilogistic.user.model.PartnerGroupMember;
import com.cognilogistic.user.repository.PartnerGroupMemberRepository;
import com.cognilogistic.user.repository.PartnerGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for partner-group management (BACKEND_GAPS §6).
 *
 * <p><strong>R5 surface:</strong>
 * <ul>
 *   <li>{@link #list} — TP-scoped, ordered by name, includes inactive groups.</li>
 *   <li>{@link #create} — TP_ADMIN only; unique-name check within TP.</li>
 *   <li>{@link #update} — partial update; {@code partnerIds} (when non-null)
 *       replaces full membership in place.</li>
 *   <li>{@link #delete} — hard delete; rejects with {@code OFFICE_HAS_ACTIVE_ORDERS}
 *       (re-using the closest existing error code) when the group is still
 *       referenced by a {@code tender_broadcast_groups} row, per the spec's
 *       "422 if the group is referenced by an active broadcast" rule.</li>
 * </ul>
 *
 * <p><strong>Permissions</strong>: gating reuses the office-CRUD pattern from
 * {@code OfficeController.requireAdmin} — only TP_ADMIN may mutate; both TP_ADMIN
 * and TP_TRANSPORT_MANAGER may read.
 */
@Service
public class PartnerGroupService {

    private final PartnerGroupRepository groups;
    private final PartnerGroupMemberRepository members;
    private final TenderBroadcastGroupRepository broadcastGroups;

    public PartnerGroupService(PartnerGroupRepository groups,
                               PartnerGroupMemberRepository members,
                               TenderBroadcastGroupRepository broadcastGroups) {
        this.groups = groups;
        this.members = members;
        this.broadcastGroups = broadcastGroups;
    }

    @Transactional(readOnly = true)
    public List<PartnerGroupDto> list(AuthPrincipal me) {
        requireTpAccount(me);
        return groups.findByTpAccountIdOrderByNameAsc(me.tpAccountId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PartnerGroupDto create(AuthPrincipal me, PartnerGroupRequest req) {
        requireAdmin(me);
        if (req.name() == null || req.name().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Group name is required",
                    Map.of("field", "name"));
        }
        // Unique-name within TP.
        groups.findByTpAccountIdAndName(me.tpAccountId(), req.name()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.OFFICE_CODE_EXISTS,
                    "A group with this name already exists",
                    Map.of("name", existing.getName()));
        });

        PartnerGroup g = new PartnerGroup();
        g.ensureId();
        g.setTpAccountId(me.tpAccountId());
        g.setName(req.name());
        g.setDescription(req.description());
        g.setActive(req.isActive() == null || req.isActive());
        g.setCreatedBy(me.userId());
        groups.save(g);

        applyMembership(g.getId(), req.partnerIds());
        return toDto(g);
    }

    @Transactional
    public PartnerGroupDto update(AuthPrincipal me, String id, PartnerGroupRequest req) {
        requireAdmin(me);
        PartnerGroup g = loadForTp(me, id);

        if (req.name() != null && !req.name().equals(g.getName())) {
            groups.findByTpAccountIdAndName(me.tpAccountId(), req.name()).ifPresent(existing -> {
                if (!existing.getId().equals(g.getId())) {
                    throw new ApiException(ErrorCode.OFFICE_CODE_EXISTS,
                            "A group with this name already exists",
                            Map.of("name", existing.getName()));
                }
            });
            g.setName(req.name());
        }
        if (req.description() != null) g.setDescription(req.description());
        if (req.isActive() != null) g.setActive(req.isActive());
        groups.save(g);

        // Membership replacement: when partnerIds is non-null we treat it as the FULL
        // intended set. Null = leave membership untouched (no-op for that field).
        if (req.partnerIds() != null) {
            applyMembership(g.getId(), req.partnerIds());
        }

        return toDto(g);
    }

    @Transactional
    public void delete(AuthPrincipal me, String id) {
        requireAdmin(me);
        PartnerGroup g = loadForTp(me, id);

        // 422 when the group is still referenced by a tender broadcast (BACKEND_GAPS §6
        // "Group write semantics" bullet 3). We reuse OFFICE_HAS_ACTIVE_ORDERS — the
        // closest semantic match — until a dedicated GROUP_HAS_BROADCASTS code lands.
        if (!broadcastGroups.findByGroupId(id).isEmpty()) {
            throw new ApiException(ErrorCode.OFFICE_HAS_ACTIVE_ORDERS,
                    "Group is still referenced by a tender broadcast — cannot delete",
                    Map.of("groupId", id));
        }

        // Cascade on partner_group_members is set via the FK, so deleting the group
        // wipes its membership rows. We delete the group itself.
        groups.delete(g);
    }

    // ===== Helpers =====

    private void requireTpAccount(AuthPrincipal me) {
        if (me == null || me.tpAccountId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not associated with a TP account");
        }
    }

    private void requireAdmin(AuthPrincipal me) {
        requireTpAccount(me);
        if (me.role() != UserRole.TP_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only TP_ADMIN can manage partner groups");
        }
    }

    private PartnerGroup loadForTp(AuthPrincipal me, String id) {
        PartnerGroup g = groups.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "Partner group not found"));
        if (!g.getTpAccountId().equals(me.tpAccountId())) {
            throw new ApiException(ErrorCode.OFFICE_NOT_FOUND, "Partner group not found");
        }
        return g;
    }

    /**
     * Replaces a group's full membership with the supplied id list. Implementation
     * note: delete-all + re-insert is simpler than diffing and is fine at UAT
     * scale (groups have ≤100 members in practice). When the count grows, swap
     * for a diff-based approach.
     */
    private void applyMembership(String groupId, List<String> partnerIds) {
        members.deleteByGroupId(groupId);
        if (partnerIds == null || partnerIds.isEmpty()) return;
        Instant now = Instant.now();
        for (String pid : partnerIds) {
            // Skip blanks and obvious junk so the row save doesn't fail later.
            if (pid != null && !pid.isBlank()) {
                members.save(new PartnerGroupMember(groupId, pid, now));
            }
        }
    }

    private PartnerGroupDto toDto(PartnerGroup g) {
        List<String> partnerIds = members.findByGroupId(g.getId()).stream()
                .map(PartnerGroupMember::getPartnerId)
                .toList();
        return new PartnerGroupDto(
                g.getId(),
                g.getName(),
                g.getDescription(),
                partnerIds,
                g.isActive(),
                g.getCreatedAt(),
                g.getUpdatedAt());
    }
}
