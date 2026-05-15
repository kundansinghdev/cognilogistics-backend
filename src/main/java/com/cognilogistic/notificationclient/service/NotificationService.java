package com.cognilogistic.notificationclient.service;

import com.cognilogistic.auth.model.User;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.notificationclient.channel.ChannelDispatchResult;
import com.cognilogistic.notificationclient.channel.NotificationChannel;
import com.cognilogistic.notificationclient.channel.Recipient;
import com.cognilogistic.notificationclient.channel.RenderedMessage;
import com.cognilogistic.notificationclient.model.Channel;
import com.cognilogistic.notificationclient.model.NotificationLog;
import com.cognilogistic.notificationclient.model.NotificationPreference;
import com.cognilogistic.notificationclient.model.NotificationStatus;
import com.cognilogistic.notificationclient.repository.NotificationLogRepository;
import com.cognilogistic.notificationclient.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central dispatcher for outbound notifications.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li><strong>Idempotency dedup</strong> — drops re-runs of the same
 *       {@code (eventKey, channel, userId)} within the in-memory cache TTL. This is the
 *       application-level workaround for v5.0 schema dropping
 *       {@code notification_log.event_id} (notification.md §10.1).</li>
 *   <li><strong>Recipient resolution</strong> — loads the user's phone / WhatsApp number /
 *       locale from {@code users}.</li>
 *   <li><strong>Preference filtering</strong> — consults
 *       {@code notification_preferences}; channels the user opted out of are recorded as
 *       {@link NotificationStatus#SKIPPED_PREFERENCE}.</li>
 *   <li><strong>Template rendering</strong> — delegates to {@link TemplateService}.</li>
 *   <li><strong>Channel dispatch</strong> — calls the right
 *       {@link com.cognilogistic.notificationclient.channel.NotificationChannel}
 *       for each channel.</li>
 *   <li><strong>Audit log</strong> — writes one {@code notification_log} row per attempted
 *       channel, recording the outcome.</li>
 * </ol>
 *
 * <p><strong>Async + AFTER_COMMIT.</strong> The {@link EventListeners} layer subscribes
 * to domain events with {@code @TransactionalEventListener(phase = AFTER_COMMIT)} and
 * calls {@link #dispatch(NotificationDispatchRequest)}. {@link #dispatch} is annotated
 * {@code @Async} so the originating request thread returns to the user before the
 * notification work begins. A failure inside the dispatcher does NOT roll back the
 * business transaction — that's the whole point of the AFTER_COMMIT split.
 *
 * <p><strong>Auto-create preferences.</strong> If a user has no
 * {@code notification_preferences} row yet (first-ever dispatch for that user) the
 * dispatcher creates a row with platform defaults rather than skipping —
 * {@link NotificationPreference#defaultsFor(String)}. The auth module's first-login
 * hook is the canonical writer of this row, but the dispatcher's lazy create is the
 * safety net for any user record that pre-existed the notification module rollout.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationLogRepository logRepository;
    private final TemplateService templateService;

    /**
     * Channel adapter map keyed by channel enum. Built from Spring's collection-injection
     * of every {@link NotificationChannel} bean — adding a new channel just means dropping
     * a new bean, no edits to this class.
     */
    private final Map<Channel, NotificationChannel> channelByType;

    /**
     * In-memory dedup cache. Key: {@code "eventKey|channel|userId"}. Value: the timestamp
     * the entry was inserted. We evict entries older than {@link #cacheTtl} and cap total
     * entries at {@link #cacheMaxEntries} (oldest-out via insertion-ordered map).
     *
     * <p><strong>Single-instance only.</strong> Horizontal scaling needs the schema's
     * {@code event_id} column back or a Redis-backed cache. Tracked in notification.md §10.1.
     */
    private final LinkedHashMap<String, Instant> dedupCache;
    private final Duration cacheTtl;
    private final int cacheMaxEntries;
    private final String primaryLocale;

    public NotificationService(UserRepository userRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               NotificationLogRepository logRepository,
                               TemplateService templateService,
                               java.util.List<NotificationChannel> channels,
                               @Value("${notifications.idempotency.cache-ttl-minutes:60}") long cacheTtlMinutes,
                               @Value("${notifications.idempotency.cache-max-entries:10000}") int cacheMaxEntries,
                               @Value("${notifications.templates.locale:hi-IN}") String primaryLocale) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.logRepository = logRepository;
        this.templateService = templateService;
        this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
        this.cacheMaxEntries = cacheMaxEntries;
        this.primaryLocale = primaryLocale;
        // Bounded LRU-ish cache. accessOrder=false (insertion order) — eldest entry is the oldest insert,
        // which combined with the size cap gives FIFO eviction. TTL-based eviction also runs on every
        // lookup so stale entries don't accumulate even if traffic is low.
        this.dedupCache = new LinkedHashMap<>(256, 0.75f, false);

        EnumMap<Channel, NotificationChannel> map = new EnumMap<>(Channel.class);
        for (NotificationChannel c : channels) {
            map.put(c.channel(), c);
        }
        this.channelByType = map;
    }

    /**
     * Dispatches a notification request — the entry point called by listeners.
     *
     * <p>Runs asynchronously on Spring's default async executor (see
     * {@code @EnableAsync} in {@link com.cognilogistic.CogniLogisticApplication}).
     *
     * @param request what to send, to whom, on which channels
     */
    @Async
    public void dispatch(NotificationDispatchRequest request) {
        try {
            doDispatch(request);
        } catch (Exception ex) {
            // We never want a notification failure to bubble out of the async executor.
            log.error("Notification dispatch failed eventKey={} userId={}",
                    request.eventKey(), request.recipientUserId(), ex);
        }
    }

    /** Internal worker. Separated so the {@code @Async} wrapper can swallow exceptions cleanly. */
    private void doDispatch(NotificationDispatchRequest request) {
        Optional<User> userOpt = userRepository.findById(request.recipientUserId());
        if (userOpt.isEmpty()) {
            log.warn("Skipping dispatch — recipient user not found. userId={} eventKey={}",
                    request.recipientUserId(), request.eventKey());
            return;
        }
        User user = userOpt.get();
        NotificationPreference prefs = loadOrCreatePreferences(user.getId());
        Recipient recipient = new Recipient(
                user.getId(),
                user.getPhone(),
                user.getWhatsappNumber(),
                primaryLocale);

        // Render once — every channel sees the same body. Channels that need to vary (e.g. an
        // in-app preview vs SMS truncation) can re-render in their adapter.
        RenderedMessage rendered = templateService.render(
                request.templateName(), recipient.locale(), request.params());

        for (Channel channel : request.channels()) {
            // Guard against listener bugs — a channel we don't have an adapter for would NPE later.
            NotificationChannel adapter = channelByType.get(channel);
            if (adapter == null) {
                log.warn("No channel adapter registered for {} — skipping. eventKey={}",
                        channel, request.eventKey());
                continue;
            }

            // Idempotency check — skip with no log row at all when we've already dispatched this
            // (event, channel, user) combination within the cache window.
            String dedupKey = request.eventKey() + "|" + channel + "|" + user.getId();
            if (alreadyDispatched(dedupKey)) {
                log.debug("Dedup hit — skipping. {}", dedupKey);
                continue;
            }

            // Preference filtering — every skipped channel still records a row so audit
            // can prove the system tried.
            if (!isChannelAllowed(channel, prefs)) {
                writeLog(user.getId(), channel, rendered.templateName(),
                        NotificationStatus.SKIPPED_PREFERENCE, null);
                rememberDispatched(dedupKey);
                continue;
            }

            // Hand off to the channel adapter; never lets exceptions escape.
            ChannelDispatchResult result;
            try {
                result = adapter.send(recipient, rendered);
            } catch (Exception ex) {
                log.error("Channel adapter threw — recording FAILED. channel={} userId={}",
                        channel, user.getId(), ex);
                result = ChannelDispatchResult.failed("adapter_exception:" + ex.getClass().getSimpleName());
            }

            String templateForLog = result.templateOverride() != null
                    ? result.templateOverride()
                    : rendered.templateName();
            writeLog(user.getId(), channel, templateForLog, result.status(), result.errorMessage());
            rememberDispatched(dedupKey);
        }
    }

    /**
     * Loads the user's preferences, creating a defaults row if none exists. Runs in
     * its own transaction so the auto-create doesn't pollute the caller's transactional
     * boundaries (the dispatcher is async and not transactional itself).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationPreference loadOrCreatePreferences(String userId) {
        return preferenceRepository.findById(userId)
                .orElseGet(() -> {
                    log.info("Auto-creating notification_preferences row for userId={}", userId);
                    return preferenceRepository.save(NotificationPreference.defaultsFor(userId));
                });
    }

    /** Maps a {@link Channel} value to the matching {@link NotificationPreference} flag. */
    private static boolean isChannelAllowed(Channel channel, NotificationPreference prefs) {
        return switch (channel) {
            case SMS -> prefs.isSmsEnabled();
            case WHATSAPP -> prefs.isWhatsappEnabled();
            case PUSH -> prefs.isPushEnabled();
            case EMAIL -> false; // pilot has no email path; column doesn't exist on preferences either
            case IN_APP -> true;  // in-app feed is unconditional — no opt-out
        };
    }

    /** Persists a {@code notification_log} row in its own transaction so failures don't poison the dispatcher. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeLog(String userId, Channel channel, String template,
                         NotificationStatus status, String errorMessage) {
        NotificationLog row = new NotificationLog();
        row.ensureId();
        row.setUserId(userId);
        row.setChannel(channel);
        // Truncate to fit the VARCHAR(100) — overflows are unusual but we don't want a save to throw.
        row.setTemplate(template == null ? null : template.substring(0, Math.min(template.length(), 100)));
        row.setStatus(status);
        row.setSentAt(Instant.now());
        row.setErrorMessage(errorMessage);
        logRepository.save(row);
    }

    // ── dedup cache helpers ─────────────────────────────────────────────────────

    /** Synchronised on the cache itself — accesses are short and contention is low in single-instance UAT. */
    private boolean alreadyDispatched(String key) {
        synchronized (dedupCache) {
            evictExpiredOnDemand();
            return dedupCache.containsKey(key);
        }
    }

    private void rememberDispatched(String key) {
        synchronized (dedupCache) {
            dedupCache.put(key, Instant.now());
            // Cap by size — drop the oldest insertion if we've exceeded the limit.
            while (dedupCache.size() > cacheMaxEntries) {
                dedupCache.remove(dedupCache.keySet().iterator().next());
            }
        }
    }

    /**
     * Walks the cache once per lookup, dropping entries older than {@link #cacheTtl}.
     * Cheap because the map is small in UAT. Production scale would use a real
     * cache library (Caffeine, etc.) — flagged in notification.md §10.1.
     */
    private void evictExpiredOnDemand() {
        Instant cutoff = Instant.now().minus(cacheTtl);
        Map<String, Instant> snapshot = new HashMap<>(dedupCache);
        for (Map.Entry<String, Instant> e : snapshot.entrySet()) {
            if (e.getValue().isBefore(cutoff)) {
                dedupCache.remove(e.getKey());
            }
        }
    }
}
