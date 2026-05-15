package com.cognilogistic.notificationclient.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.notificationclient.dto.NotificationPreferenceDto;
import com.cognilogistic.notificationclient.dto.UpdatePreferencesRequest;
import com.cognilogistic.notificationclient.service.PreferenceService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the user's own notification channel preferences.
 *
 * <p>Mounted at {@code /api/v1/me/notification-preferences} — the {@code /me/}
 * convention matches the rest of the platform's "my profile" surface.
 *
 * <p>Authentication is enforced by Spring Security; if the JWT is missing the global
 * security configuration returns 401 before this controller runs, so no manual auth
 * checks are needed here.
 */
@Tag(name = "Notification preferences", description = "Current user's channel toggles. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/me/notification-preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * Returns the caller's preferences row, creating defaults on first access.
     *
     * @param me the authenticated principal
     * @return the four channel toggles (sms / whatsapp / push)
     */
    @GetMapping
    public ApiResponse<NotificationPreferenceDto> get(@CurrentUser AuthPrincipal me) {
        return ApiResponse.ok(NotificationPreferenceDto.from(preferenceService.getOrCreate(me.userId())));
    }

    /**
     * Partial update of the caller's preferences. Null fields on the body are left
     * unchanged; non-null fields overwrite the existing value.
     *
     * @param me      the authenticated principal
     * @param request the partial update payload
     * @return the updated preferences
     */
    @PatchMapping
    public ApiResponse<NotificationPreferenceDto> patch(@CurrentUser AuthPrincipal me,
                                                        @Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.ok(NotificationPreferenceDto.from(
                preferenceService.update(me.userId(), request)));
    }
}
