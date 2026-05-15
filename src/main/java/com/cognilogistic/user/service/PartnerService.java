package com.cognilogistic.user.service;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.tender.model.PartnerTpProfile;
import com.cognilogistic.tender.repository.PartnerTpProfileRepository;
import com.cognilogistic.user.dto.PartnerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read service for Logistics Partners (BACKEND_GAPS §6).
 *
 * <p><strong>R5 scope</strong> — list active partners and resolve a single
 * partner by id. Listing is intentionally global: the FE's "My Network" tab
 * shows every partner the platform knows about and the TP picks who goes into
 * their groups. The {@code tp_partner_network} table (schema.sql v5.0
 * lines 232–242) is not yet a Flyway migration; once it lands, this service's
 * list method will scope by that junction.
 *
 * <p><strong>Cross-module reach:</strong> {@link PartnerTpProfile} lives in
 * {@code com.cognilogistic.tender.model} (added by R4). The user module imports
 * it directly here rather than duplicating the entity. A future refactor may
 * move the partner profile under user/, but the entity name and column shape
 * are stable enough that the cross-import is acceptable for UAT.
 *
 * <p>Display name comes from {@link PartnerTpProfile#getCompanyName()}; phone
 * is fetched from the linked {@code users} row. Vehicle types and region are
 * deferred to the post-R5 entity expansion.
 */
@Service
public class PartnerService {

    private final PartnerTpProfileRepository partners;
    private final UserRepository users;

    public PartnerService(PartnerTpProfileRepository partners, UserRepository users) {
        this.partners = partners;
        this.users = users;
    }

    /**
     * Lists every partner the platform knows about (active or otherwise — the FE
     * filters client-side). Returned ordered by company name.
     */
    @Transactional(readOnly = true)
    public List<PartnerDto> list() {
        return partners.findAll().stream()
                .sorted((a, b) -> {
                    String an = a.getCompanyName() == null ? "" : a.getCompanyName();
                    String bn = b.getCompanyName() == null ? "" : b.getCompanyName();
                    return an.compareToIgnoreCase(bn);
                })
                .map(this::toDto)
                .toList();
    }

    /** Builds the wire {@link PartnerDto} for a single partner. Joins to {@code users} for phone. */
    private PartnerDto toDto(PartnerTpProfile p) {
        // The owning user's phone is the partner's contact number — the partner_tp_profiles
        // table itself doesn't carry a phone column (it's the user's primary identity).
        String phone = users.findById(p.getUserId())
                .map(u -> u.getPhone())
                .orElse(null);

        return new PartnerDto(
                p.getId(),
                p.getCompanyName(),
                phone,
                List.of(),    // vehicleTypes — populated post-R5 when the profile entity widens
                null);        // region — same: post-R5 expansion
    }
}
