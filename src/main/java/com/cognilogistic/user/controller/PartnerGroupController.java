package com.cognilogistic.user.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.user.dto.PartnerGroupDto;
import com.cognilogistic.user.dto.PartnerGroupRequest;
import com.cognilogistic.user.service.PartnerGroupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for partner-group CRUD at {@code /api/v1/partner-groups}
 * (BACKEND_GAPS §6).
 *
 * <p>Read open to any authenticated TP user; mutations restricted to TP_ADMIN
 * (enforced inside {@link PartnerGroupService}). Membership is replaced in place
 * via {@code PATCH partnerIds} — see {@link PartnerGroupRequest} for semantics.
 */
@Tag(name = "Partner groups", description = "TP partner broadcast groups. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/partner-groups")
public class PartnerGroupController {

    private final PartnerGroupService groups;

    public PartnerGroupController(PartnerGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    public ApiResponse<List<PartnerGroupDto>> list(@CurrentUser AuthPrincipal me) {
        return ApiResponse.ok(groups.list(me));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PartnerGroupDto> create(@CurrentUser AuthPrincipal me,
                                                @Valid @RequestBody PartnerGroupRequest req) {
        return ApiResponse.ok(groups.create(me, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PartnerGroupDto> update(@CurrentUser AuthPrincipal me,
                                                @PathVariable String id,
                                                @Valid @RequestBody PartnerGroupRequest req) {
        return ApiResponse.ok(groups.update(me, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        groups.delete(me, id);
    }
}
