package com.cognilogistic.notificationclient.repository;

import com.cognilogistic.notificationclient.model.Channel;
import com.cognilogistic.notificationclient.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the append-only {@code notification_log} table.
 *
 * <p>The hot reads are the in-app feed list ({@link #findByUserIdAndChannelOrderBySentAtDesc})
 * and a single-row fetch by id ({@link #findById}). UPDATEs are not expected — this
 * is an append-only log, except for the post-UAT "mark read" feature which we
 * implement with client-side state (notification.md §10.4) so the column doesn't
 * exist server-side.
 *
 * <p>Pagination uses Spring Data's {@link Pageable} so the controller can pass
 * {@code page} / {@code size} through directly.
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {

    /**
     * Returns the user's in-app feed — every {@link Channel#IN_APP} entry, newest first.
     * Backed by {@code idx_nl_user} on {@code (user_id)} plus a sort on {@code sent_at}.
     *
     * @param userId  the recipient user id (CHAR(36))
     * @param channel typically {@link Channel#IN_APP} for the feed; the parameter is here
     *                so support tooling can reuse the same query for any channel
     * @param page    pagination
     * @return one page of log rows, newest first
     */
    Page<NotificationLog> findByUserIdAndChannelOrderBySentAtDesc(String userId, Channel channel, Pageable page);
}
