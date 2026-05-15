package com.cognilogistic.legal.controller;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.legal.dto.UserConsentDto;
import com.cognilogistic.legal.repository.UserConsentRepository;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only endpoint that returns a single user's full T&amp;C / Privacy consent
 * history, ordered most-recent-first. Spec §4.3.
 *
 * <p>Mounted at {@code /api/v1/admin/users/{userId}/consents}. Restricted to
 * {@link UserRole#COGNILOGISTIC_ADMIN} — any other authenticated role gets
 * 403 {@link ErrorCode#FORBIDDEN}. Note: the spec uses the name
 * {@code COGNI_ADMIN}; the project's canonical enum value is
 * {@code COGNILOGISTIC_ADMIN} — semantically the same role.
 *
 * <p>The endpoint is informational only — no writes, no PII redaction. IP and
 * User-Agent are returned verbatim because the audit use case needs them.
 */
@Tag(name = "Admin — consents", description = "Per-user consent audit history. Admin JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserConsentController {

    private final UserConsentRepository userConsents;

    public AdminUserConsentController(UserConsentRepository userConsents) {
        this.userConsents = userConsents;
    }

    /**
     * Returns the full consent history for one user, ordered by acceptance
     * time descending (most recent first).
     *
     * @param userId the user whose consents to retrieve (CHAR(36) UUID)
     * @param me     the calling admin's identity (must be COGNILOGISTIC_ADMIN)
     * @return list of consent rows; empty list if the user has none
     * @throws ApiException with {@link ErrorCode#FORBIDDEN} if the caller is not an admin
     */
    @GetMapping("/{userId}/consents")
    public ApiResponse<List<UserConsentDto>> userConsents(@PathVariable String userId,
                                                          @CurrentUser AuthPrincipal me) {
        requireAdmin(me);
        List<UserConsentDto> dtos = userConsents.findByUserIdOrderByAcceptedAtDesc(userId).stream()
                .map(UserConsentDto::from)
                .toList();
        return ApiResponse.ok(dtos);
    }

    /** Asserts the caller is the platform admin (cross-tenant audit role). */
    private void requireAdmin(AuthPrincipal me) {
        if (me == null || me.role() != UserRole.COGNILOGISTIC_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only CogniLogistic platform admins can read consent history.");
        }
    }
}
