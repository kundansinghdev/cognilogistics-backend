package com.cognilogistic.notificationclient.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON for a paginated notification feed — avoids serializing {@link Page}
 * / {@code PageImpl} directly (Spring Data warns and does not guarantee wire stability).
 */
public record NotificationPageResponse(
        List<NotificationDto> content,
        long totalElements,
        int totalPages,
        int size,
        int number) {

    public static NotificationPageResponse from(Page<NotificationDto> page) {
        return new NotificationPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber());
    }
}
