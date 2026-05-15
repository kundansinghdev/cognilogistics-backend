package com.cognilogistic.notificationclient.service;

import com.cognilogistic.notificationclient.dto.NotificationDto;
import com.cognilogistic.notificationclient.model.Channel;
import com.cognilogistic.notificationclient.repository.NotificationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only feed operations for {@code GET /api/v1/notifications}.
 *
 * <p>Returns the user's IN_APP rows from {@code notification_log}, newest first, in
 * pages. Read-state ("read / unread") is client-side per notification.md §10.4 — the
 * server does not track it for UAT, so this service intentionally has no
 * {@code markAsRead} method.
 */
@Service
public class NotificationFeedService {

    /** Cap on requested page size — keeps any one query bounded regardless of client input. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationLogRepository logRepository;

    public NotificationFeedService(NotificationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Returns one page of the user's in-app feed.
     *
     * @param userId   the authenticated user id (CHAR(36))
     * @param page     zero-indexed page number; clamped to ≥0
     * @param size     page size; clamped to {@code [1, 100]}
     * @return page of notification DTOs, newest first
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> list(String userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return logRepository.findByUserIdAndChannelOrderBySentAtDesc(
                        userId, Channel.IN_APP, PageRequest.of(safePage, safeSize))
                .map(NotificationDto::from);
    }
}
