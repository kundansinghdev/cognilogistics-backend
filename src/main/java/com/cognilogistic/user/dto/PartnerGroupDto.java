package com.cognilogistic.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * Wire DTO for a partner group (BACKEND_GAPS §6).
 *
 * <p>Returned by {@code GET /api/v1/partner-groups} and the create / update / delete
 * paths. {@link #partnerIds} is always the canonical membership list (the read
 * path joins {@code partner_group_members}).
 *
 * @param id          group UUID
 * @param name        display name (unique per TP)
 * @param description optional description
 * @param partnerIds  current member partner UUIDs
 * @param isActive    soft-delete flag
 * @param createdAt   creation timestamp
 * @param updatedAt   last-modified timestamp
 */
public record PartnerGroupDto(
        String id,
        String name,
        String description,
        List<String> partnerIds,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
