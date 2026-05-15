package com.cognilogistic.user.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/partner-groups} and {@code PATCH /api/v1/partner-groups/{id}}.
 *
 * <p>BACKEND_GAPS §6 group write semantics:
 * <ul>
 *   <li>{@code POST} — {@code name} required; {@code description} and
 *       {@code partnerIds} optional.</li>
 *   <li>{@code PATCH} — all fields optional. {@code partnerIds} is treated as the
 *       <em>full</em> intended membership list — the service replaces existing
 *       membership in place (delete-all + re-insert). The FE sends the complete
 *       array on every membership toggle.</li>
 * </ul>
 *
 * @param name        display name (≤150 chars; unique within the TP)
 * @param description optional description (≤500 chars)
 * @param partnerIds  full membership list — replaces existing on PATCH
 * @param isActive    soft-delete flag (PATCH only)
 */
public record PartnerGroupRequest(

        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        List<String> partnerIds,

        Boolean isActive
) {}
