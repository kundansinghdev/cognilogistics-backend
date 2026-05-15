package com.cognilogistic.tender.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/tenders/{id}/broadcast} (BACKEND_GAPS §5.2).
 *
 * <p>Two distinct broadcast modes share one endpoint:
 *
 * <ol>
 *   <li><strong>App / in-network</strong> — {@code channel = "app"} with one or more
 *       {@code groupIds}. The tender is recorded in {@code tender_broadcast_groups}
 *       so partners belonging to those groups see it in their partner-portal feed.</li>
 *   <li><strong>WhatsApp out-of-network</strong> — {@code channel = "whatsapp"} with
 *       a single {@code whatsappNumber}. Generates a {@code wa.me/...} link the TP
 *       forwards manually (mirrors the Notification module's WhatsApp template flow).
 *       No partner_groups row written.</li>
 * </ol>
 *
 * <p>Both modes append the channel string to the tender's {@code sent_via} JSON
 * array and recompute {@code broadcast_partner_count}.
 *
 * <p>Mode selection is determined by the {@link #channel} value; the unused
 * fields can be {@code null}. The service validates per-mode requirements
 * (e.g. groupIds non-empty for {@code "app"}).
 *
 * @param channel         broadcast mode — must be {@code "app"} or {@code "whatsapp"}
 * @param groupIds        partner_group UUIDs to broadcast to (required for {@code "app"})
 * @param whatsappNumber  10–15 digit phone for the WhatsApp link (required for {@code "whatsapp"})
 */
public record TenderBroadcastRequest(

        @NotBlank
        String channel,

        List<String> groupIds,

        String whatsappNumber
) {}
