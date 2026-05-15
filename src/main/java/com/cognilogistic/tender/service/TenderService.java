package com.cognilogistic.tender.service;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.model.OrderType;
import com.cognilogistic.order.service.OrderService;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.tender.dto.BidDto;
import com.cognilogistic.tender.dto.CreateTenderRequest;
import com.cognilogistic.tender.dto.TenderAwardRequest;
import com.cognilogistic.tender.dto.TenderBroadcastRequest;
import com.cognilogistic.tender.dto.TenderDto;
import com.cognilogistic.tender.model.Bid;
import com.cognilogistic.tender.model.BidStatus;
import com.cognilogistic.tender.model.PartnerTpProfile;
import com.cognilogistic.tender.model.Tender;
import com.cognilogistic.tender.model.TenderBroadcastGroup;
import com.cognilogistic.tender.model.TenderOrderRef;
import com.cognilogistic.tender.model.TenderStatus;
import com.cognilogistic.tender.repository.BidRepository;
import com.cognilogistic.tender.repository.PartnerTpProfileRepository;
import com.cognilogistic.tender.repository.TenderBroadcastGroupRepository;
import com.cognilogistic.tender.repository.TenderOrderRefRepository;
import com.cognilogistic.tender.repository.TenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Service for tender lifecycle operations on the TP side (BACKEND_GAPS §5).
 *
 * <p><strong>R4 surface:</strong>
 * <ul>
 *   <li>{@link #create} — DRAFT tender (with optional PTL consolidation).</li>
 *   <li>{@link #list}, {@link #get} — TP-scoped reads with full bid + broadcast context.</li>
 *   <li>{@link #broadcast} — DRAFT → IN_PROGRESS, append to {@code sent_via}, write
 *       {@code tender_broadcast_groups} rows for app-channel broadcasts.</li>
 *   <li>{@link #award} — IN_PROGRESS → COMPLETED, winning bid → ACCEPTED, siblings → REJECTED.</li>
 * </ul>
 *
 * <p>Partner-side surface (place bid / submit assignment) lands in PR R7.
 *
 * <p><strong>Wire status mapping</strong> — the service stores
 * {@link BidStatus#ACCEPTED} for the winning bid (canonical schema name) but the
 * front-end's wire format expects {@code "AWARDED"}. {@link #toBidDto} performs
 * the translation so the schema stays untouched.
 */
@Service
public class TenderService {

    private static final DateTimeFormatter TENDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Allowed broadcast channels — extend as new channels come online. */
    private static final Set<String> ALLOWED_CHANNELS = Set.of("app", "whatsapp");

    private final TenderRepository tenderRepo;
    private final TenderOrderRefRepository tenderOrderRefRepo;
    private final BidRepository bidRepo;
    private final PartnerTpProfileRepository partnerRepo;
    private final TenderBroadcastGroupRepository broadcastGroupRepo;
    private final OrderService orderService;

    public TenderService(TenderRepository tenderRepo,
                         TenderOrderRefRepository tenderOrderRefRepo,
                         BidRepository bidRepo,
                         PartnerTpProfileRepository partnerRepo,
                         TenderBroadcastGroupRepository broadcastGroupRepo,
                         OrderService orderService) {
        this.tenderRepo = tenderRepo;
        this.tenderOrderRefRepo = tenderOrderRefRepo;
        this.bidRepo = bidRepo;
        this.partnerRepo = partnerRepo;
        this.broadcastGroupRepo = broadcastGroupRepo;
        this.orderService = orderService;
    }

    // ===== Create =====

    /**
     * Creates a DRAFT tender. If {@code orderIds} are supplied, validates and links
     * them: each must be PTL and ACKNOWLEDGED or FLEET_CONFIRMED. The legacy
     * {@code Tender#weightKg} is set as the sum of linked weights — it's
     * {@code @Transient} on the entity so the value is for response convenience only.
     *
     * <p>Returns a {@link TenderDto} so the FE has the full hydrated shape (status,
     * tender number, broadcast / bid context — all empty at creation but typed).
     */
    @Transactional
    public TenderDto create(AuthPrincipal me, CreateTenderRequest req) {
        requireTpAccount(me);

        Tender tender = new Tender();
        tender.setTpAccountId(me.tpAccountId());
        // BACKEND_GAPS doesn't change this — DRAFT until the TP explicitly broadcasts.
        // Old behaviour was IN_PROGRESS at create; flipping to DRAFT here matches the
        // schema default and the front-end's "save draft → broadcast later" UX.
        tender.setStatus(TenderStatus.DRAFT);
        tender.setCreatedBy(me.userId());
        // Title falls back to description so the FE has something to show; FE can
        // PATCH a real title via a future endpoint (not in R4 scope).
        tender.setTitle(req.description());
        tender.setNotes(req.description());
        tender.setBroadcastPartnerCount(0);
        tender.setTenderNumber(nextTenderNo(me.tpAccountId(), LocalDate.now(ZoneOffset.UTC)));

        if (req.orderIds() != null && !req.orderIds().isEmpty()) {
            List<Order> sourceOrders = orderService.findAllByIds(req.orderIds(), me.tpAccountId());

            for (Order o : sourceOrders) {
                if (o.getOrderType() != OrderType.PTL) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "Only PTL orders can be consolidated into a tender",
                            Map.of("orderId", o.getId()));
                }
                if (!EnumSet.of(OrderStatus.ACKNOWLEDGED, OrderStatus.FLEET_CONFIRMED).contains(o.getStatus())) {
                    throw new ApiException(ErrorCode.INVALID_TRANSITION,
                            "Order must be ACKNOWLEDGED or FLEET_CONFIRMED",
                            Map.of("orderId", o.getId()));
                }
            }

            int totalWeightKg = sourceOrders.stream()
                    .mapToInt(o -> o.getWeightKg() != null ? o.getWeightKg().intValue() : 0)
                    .sum();
            tender.setWeightKg(totalWeightKg);

            Tender saved = tenderRepo.save(tender);
            for (Order o : sourceOrders) {
                tenderOrderRefRepo.save(new TenderOrderRef(saved.getId(), o.getId()));
            }
            return toDto(saved);
        }

        return toDto(tenderRepo.save(tender));
    }

    // ===== Read =====

    @Transactional(readOnly = true)
    public List<TenderDto> list(AuthPrincipal me) {
        requireTpAccount(me);
        return tenderRepo.findByTpAccountIdOrderByCreatedAtDesc(me.tpAccountId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenderDto get(AuthPrincipal me, String id) {
        return toDto(loadTenderForTp(me, id));
    }

    // ===== Broadcast =====

    /**
     * Broadcasts a tender (BACKEND_GAPS §5.2). Two modes share this entry point:
     *
     * <ul>
     *   <li>{@code channel="app"} — writes one {@code tender_broadcast_groups} row
     *       per supplied {@code groupId}; partners belonging to those groups see
     *       the tender on their partner-portal feed (R7 visibility filter).</li>
     *   <li>{@code channel="whatsapp"} — generates a {@code wa.me/...} link the TP
     *       forwards manually. No group rows written. The WhatsApp number is
     *       captured for audit but not stored on the tender row (mirrors the
     *       Notification module's WhatsApp-template flow).</li>
     * </ul>
     *
     * <p>Both modes append the channel string to the tender's {@code sent_via}
     * JSON array (idempotently — duplicates are deduped) and recompute
     * {@code broadcast_partner_count}. Status moves DRAFT → IN_PROGRESS on the
     * first broadcast; subsequent broadcasts on the same tender are also accepted
     * (re-broadcast pattern), at which point status remains IN_PROGRESS.
     */
    @Transactional
    public TenderDto broadcast(AuthPrincipal me, String tenderId, TenderBroadcastRequest req) {
        Tender tender = loadTenderForTp(me, tenderId);

        if (tender.getStatus() == TenderStatus.COMPLETED || tender.getStatus() == TenderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Tender in status " + tender.getStatus() + " cannot be broadcast");
        }

        String channel = req.channel() == null ? "" : req.channel().toLowerCase();
        if (!ALLOWED_CHANNELS.contains(channel)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported broadcast channel: " + channel,
                    Map.of("allowed", ALLOWED_CHANNELS));
        }

        // App / in-network broadcast: write a row per group. Skip duplicates so
        // re-broadcast doesn't blow up on the composite-key constraint.
        if ("app".equals(channel)) {
            if (req.groupIds() == null || req.groupIds().isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "groupIds required for channel=app",
                        Map.of("field", "groupIds"));
            }
            Set<String> existing = broadcastGroupRepo.findByTenderId(tenderId).stream()
                    .map(TenderBroadcastGroup::getGroupId)
                    .collect(java.util.stream.Collectors.toSet());
            for (String gid : req.groupIds()) {
                if (!existing.contains(gid)) {
                    broadcastGroupRepo.save(new TenderBroadcastGroup(tenderId, gid, Instant.now()));
                }
            }
        } else {
            // WhatsApp out-of-network: just an audit / signal channel. The wa.me link
            // generation lives on the FE (or notification module via a separate event).
            if (req.whatsappNumber() == null || req.whatsappNumber().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "whatsappNumber required for channel=whatsapp",
                        Map.of("field", "whatsappNumber"));
            }
        }

        // Append the channel to sent_via (deduped).
        Set<String> sentVia = new java.util.LinkedHashSet<>(parseSentVia(tender.getSentViaJson()));
        sentVia.add(channel);
        tender.setSentViaJson(serialiseStringList(new ArrayList<>(sentVia)));

        // Refresh the cached partner count. R4 approximation: count = number of distinct
        // groups broadcast to. Partner-distinct counting (joining partner_group_members)
        // lands in R5 once that entity is wired up.
        int groupCount = broadcastGroupRepo.findByTenderId(tenderId).size();
        tender.setBroadcastPartnerCount(groupCount);

        if (tender.getStatus() == TenderStatus.DRAFT) {
            tender.setStatus(TenderStatus.IN_PROGRESS);
        }

        return toDto(tenderRepo.save(tender));
    }

    // ===== Award =====

    /**
     * Awards a tender (BACKEND_GAPS §5.3). Validates that the bid belongs to this
     * tender and that the tender is IN_PROGRESS (no awarding a CANCELLED tender,
     * no double-awarding a COMPLETED one), then:
     * <ul>
     *   <li>Tender: status → COMPLETED, {@code awardedBidId} = winning bid,
     *       {@code awardedTo} = winning partner.</li>
     *   <li>Winning bid: status → ACCEPTED.</li>
     *   <li>All sibling bids on this tender: PENDING → REJECTED.</li>
     * </ul>
     */
    @Transactional
    public TenderDto award(AuthPrincipal me, String tenderId, TenderAwardRequest req) {
        Tender tender = loadTenderForTp(me, tenderId);

        if (tender.getStatus() != TenderStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Tender must be IN_PROGRESS to award; current status: " + tender.getStatus());
        }

        Bid winning = bidRepo.findById(req.bidId())
                .filter(b -> b.getTenderId().equals(tenderId))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Bid does not belong to this tender",
                        Map.of("bidId", req.bidId())));

        // Winning bid → ACCEPTED.
        winning.setStatus(BidStatus.ACCEPTED);
        bidRepo.save(winning);

        // Sibling PENDING bids → REJECTED. WITHDRAWN bids stay WITHDRAWN.
        for (Bid b : bidRepo.findByTenderIdAndStatus(tenderId, BidStatus.PENDING)) {
            if (!b.getId().equals(winning.getId())) {
                b.setStatus(BidStatus.REJECTED);
                bidRepo.save(b);
            }
        }

        tender.setStatus(TenderStatus.COMPLETED);
        tender.setAwardedBidId(winning.getId());
        tender.setAwardedTo(winning.getPartnerId());
        return toDto(tenderRepo.save(tender));
    }

    // ===== Helpers =====

    private void requireTpAccount(AuthPrincipal me) {
        if (me == null || me.tpAccountId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not associated with a TP account");
        }
    }

    private Tender loadTenderForTp(AuthPrincipal me, String tenderId) {
        requireTpAccount(me);
        Tender t = tenderRepo.findById(tenderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Tender not found"));
        if (!t.getTpAccountId().equals(me.tpAccountId())) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Tender not found");
        }
        return t;
    }

    /**
     * Generates the next {@code tender_number} for a TP on a given date.
     * Format: {@code TND-YYYYMMDD-NNNN}. Same race-tolerance pattern as
     * {@code OrderService#nextOrderNo}.
     */
    private String nextTenderNo(String tpAccountId, LocalDate date) {
        String prefix = "TND-" + date.format(TENDER_NO_DATE_FORMAT) + "-";
        long existing = tenderRepo.countByTpAccountIdAndTenderNumberStartingWith(tpAccountId, prefix);
        return prefix + String.format("%04d", existing + 1);
    }

    /** Parses {@code sent_via} JSON into a List. Tolerates null, empty string, and malformed JSON. */
    private static List<String> parseSentVia(String json) {
        if (json == null || json.isBlank()) return List.of();
        // Minimal JSON-array parser — the column shape is always
        // {@code ["app","whatsapp"]} or {@code []}, so we don't pull in Jackson here.
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return List.of();
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) return List.of();
        return Arrays.stream(inner.split(","))
                .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Serialises a string list to a JSON-array string compatible with {@link #parseSentVia}. */
    private static String serialiseStringList(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Public alias for {@link #toDto} — exposed so the partner-side
     * {@link PartnerTenderService} can build the same wire payload without
     * duplicating the bid / broadcast / order hydration logic. Identical output
     * for both the TP and partner views in R7; future iterations may diverge
     * (e.g. masking award details on the losing-partners view).
     */
    public TenderDto buildPartnerView(Tender t) {
        return toDto(t);
    }

    /** Builds the wire DTO with bids, broadcast groups, and order ids hydrated. */
    private TenderDto toDto(Tender t) {
        // Linked PTL orders.
        List<String> orderIds = tenderOrderRefRepo.findByTenderId(t.getId()).stream()
                .map(TenderOrderRef::getOrderId)
                .toList();

        // Broadcast groups.
        List<String> broadcastGroupIds = broadcastGroupRepo.findByTenderId(t.getId()).stream()
                .map(TenderBroadcastGroup::getGroupId)
                .toList();

        // Bids — with partner names denormalised for display.
        List<Bid> bids = bidRepo.findByTenderIdOrderBySubmittedAtAsc(t.getId());
        List<BidDto> bidDtos = bids.stream().map(this::toBidDto).toList();

        return new TenderDto(
                t.getId(),
                t.getTpAccountId(),
                t.getTenderNumber(),
                t.getStatus(),
                t.getTitle(),
                t.getOrigin(),
                t.getDestination(),
                t.getVehicleType(),
                t.getGoodsType(),
                t.getPickupDate(),
                t.getDeliveryDate(),
                t.getRefPriceInr(),
                t.getNotes(),
                orderIds,
                broadcastGroupIds,
                t.getBroadcastPartnerCount(),
                parseSentVia(t.getSentViaJson()),
                bidDtos.size(),
                bidDtos,
                t.getAwardedBidId(),
                t.getAwardedTo(),
                t.getCreatedBy(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    /**
     * Builds the wire {@link BidDto} for a {@link Bid}. Looks up the partner's
     * display name (single fetch per bid — N+1 acceptable at UAT scale; PR R7
     * batch-fetches when the partner-portal list view lands).
     *
     * <p>Status translation: {@link BidStatus#ACCEPTED} renders as {@code "AWARDED"}
     * on the wire to match the FE convention.
     */
    private BidDto toBidDto(Bid b) {
        PartnerTpProfile partner = partnerRepo.findById(b.getPartnerId()).orElse(null);
        String partnerName = partner == null ? null : partner.getCompanyName();
        String wireStatus = b.getStatus() == BidStatus.ACCEPTED ? "AWARDED" : b.getStatus().name();
        return new BidDto(
                b.getId(),
                b.getTenderId(),
                b.getPartnerId(),
                partnerName,
                b.getAmountInr(),
                b.getEtaDays(),
                b.getNotes(),
                wireStatus,
                b.getSubmittedAt());
    }

    /**
     * Helper for cross-module reads (e.g. order service surfacing tenderNumber on
     * an order DTO). Not used in R4 yet, but exposed so PR R5+ can avoid double
     * imports across modules.
     */
    @SuppressWarnings("unused")
    private static <T> Stream<T> nullSafeStream(List<T> list) {
        return list == null ? Stream.empty() : list.stream();
    }
}
