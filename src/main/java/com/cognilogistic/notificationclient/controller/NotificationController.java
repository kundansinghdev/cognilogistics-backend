package com.cognilogistic.notificationclient.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.notificationclient.dto.NotificationPageResponse;
import com.cognilogistic.notificationclient.service.NotificationFeedService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the user's in-app notification feed.
 *
 * <p>{@code GET /api/v1/notifications} returns the IN_APP rows from
 * {@code notification_log} for the authenticated caller, newest first, paginated.
 *
 * <p>Read / unread state is client-side for UAT (notification.md §10.4) — there is
 * no {@code POST /{id}/read} endpoint here. The mobile / web app tracks read status
 * in localStorage. If multi-device sync is needed post-UAT, schema needs a
 * {@code read_at} column and a corresponding endpoint lands then.
 */
@Tag(name = "Notifications", description = "In-app notification feed. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationFeedService feedService;

    public NotificationController(NotificationFeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * Lists the caller's in-app feed, newest first.
     *
     * @param me   the authenticated principal
     * @param page zero-indexed page number (default 0)
     * @param size page size (default 20, max 100 — enforced by service)
     * @return one page of feed entries
     */
    @GetMapping
    public ApiResponse<NotificationPageResponse> list(@CurrentUser AuthPrincipal me,
                                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                                      @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(NotificationPageResponse.from(feedService.list(me.userId(), page, size)));
    }
}
