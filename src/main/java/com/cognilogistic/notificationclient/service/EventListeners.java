package com.cognilogistic.notificationclient.service;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.notificationclient.model.Channel;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.order.service.OrderEvents;
import com.cognilogistic.tender.service.TenderEvents;
import com.cognilogistic.user.service.UserAccountEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Subscribes to domain events from order / tender / user modules and converts them into
 * {@link NotificationDispatchRequest}s that the {@link NotificationService} sends.
 *
 * <p><strong>Why @TransactionalEventListener with phase=AFTER_COMMIT</strong>
 * (notification.md §3.1): notifications must NEVER fire if the originating business
 * transaction rolls back. Spring's {@code @TransactionalEventListener} with
 * {@code AFTER_COMMIT} delivers the event only after the producer's transaction
 * commits successfully. A regular {@code @EventListener} would fire synchronously
 * inside the producer's transaction — a notification could go out and then the
 * caller's update could roll back, leaving the customer with an "order delivered"
 * SMS for a DB row that no longer says delivered.
 *
 * <p><strong>Recipient resolution.</strong> Domain events typically don't carry the
 * recipient's user id directly — for an order event we look up the order, then the
 * customer, then the {@link com.cognilogistic.auth.model.User} keyed by the customer's
 * phone. If the user lookup fails (e.g. the customer is shadow with no user record),
 * we silently skip — no notification can be delivered with no user to address.
 *
 * <p><strong>Channel matrix.</strong> Each handler hard-codes the channel set per
 * event type per notification.md §3.2. If channels need to become configurable later
 * (e.g. tenant-level overrides), the matrix moves to a config table; for UAT it lives
 * in code.
 *
 * <p>This listener does not catch {@code Exception} — the dispatcher already does, and
 * we want any genuine programming error in the listener (e.g. NPE on a malformed event)
 * to surface in the application log rather than be silently dropped.
 */
@Component
public class EventListeners {

    private static final Logger log = LoggerFactory.getLogger(EventListeners.class);

    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public EventListeners(NotificationService notificationService,
                          OrderRepository orderRepository,
                          CustomerRepository customerRepository,
                          UserRepository userRepository) {
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    // ── Order lifecycle ────────────────────────────────────────────────────────

    /**
     * On order creation: notify the customer (SMS + In-App) and the assigned office staff
     * (In-App). The TP-side notification is "an order was just created in your office"
     * which the assigned-office TP_TRANSPORT_MANAGER sees in their feed.
     */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderEvents.OrderCreated e) {
        // Customer notification (SMS + In-App) — only if a User row exists for the customer's phone.
        Optional<String> customerUserId = resolveCustomerUserId(e.customerId());
        Map<String, String> params = orderParams(e.orderId(), null);
        customerUserId.ifPresent(userId -> notificationService.dispatch(new NotificationDispatchRequest(
                "OrderCreated:" + e.orderId(),
                userId,
                List.of(Channel.SMS, Channel.IN_APP),
                "ORDER_CREATED",
                params)));
    }

    /**
     * On every order status transition: notify the customer per channel matrix
     * (SMS + In-App for IN_TRANSIT / DELIVERED / CANCELLED, In-App-only for ACK / FLEET_CONFIRMED).
     */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderEvents.OrderStatusChanged e) {
        Order order = orderRepository.findById(e.orderId()).orElse(null);
        if (order == null) {
            log.warn("OrderStatusChanged for missing orderId={} — skipping notifications.", e.orderId());
            return;
        }
        Optional<String> customerUserId = resolveCustomerUserId(order.getCustomerId());
        if (customerUserId.isEmpty()) {
            return; // shadow customer or no user yet — nothing to deliver to
        }
        Map<String, String> params = orderParams(order.getId(), order.getVehicleRegistration());
        String userId = customerUserId.get();

        // Per the channel matrix in notification.md §3.2.
        switch (e.to()) {
            case ACKNOWLEDGED -> notificationService.dispatch(new NotificationDispatchRequest(
                    "OrderAcknowledged:" + order.getId(),
                    userId,
                    List.of(Channel.IN_APP),
                    "ORDER_ACKNOWLEDGED",
                    params));
            case FLEET_CONFIRMED -> notificationService.dispatch(new NotificationDispatchRequest(
                    "OrderFleetConfirmed:" + order.getId(),
                    userId,
                    List.of(Channel.SMS, Channel.IN_APP),
                    "ORDER_FLEET_CONFIRMED",
                    params));
            case IN_TRANSIT -> notificationService.dispatch(new NotificationDispatchRequest(
                    "OrderInTransit:" + order.getId(),
                    userId,
                    List.of(Channel.SMS, Channel.WHATSAPP, Channel.IN_APP),
                    "ORDER_IN_TRANSIT",
                    params));
            case DELIVERED -> notificationService.dispatch(new NotificationDispatchRequest(
                    "OrderDelivered:" + order.getId(),
                    userId,
                    List.of(Channel.SMS, Channel.IN_APP),
                    "ORDER_DELIVERED",
                    params));
            case CANCELLED -> {
                Map<String, String> cancelParams = new HashMap<>(params);
                cancelParams.put("reason", "—"); // schema note: cancelledReason is @Transient on Order
                notificationService.dispatch(new NotificationDispatchRequest(
                        "OrderCancelled:" + order.getId(),
                        userId,
                        List.of(Channel.SMS, Channel.IN_APP),
                        "ORDER_CANCELLED",
                        cancelParams));
            }
            default -> { /* CREATED is handled by onOrderCreated; no other terminal states */ }
        }
    }

    // ── Tender lifecycle ───────────────────────────────────────────────────────

    /**
     * Tender publication: the WhatsApp template is the manual-forward path the TP_ADMIN
     * uses to invite their LP partners (no BSP). In-App goes to invited LPs once their
     * partner profile gets a User record; for UAT we record the LP-side fanout when
     * those producer-side wires land.
     */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onTenderPublished(TenderEvents.TenderPublished e) {
        // Invited-LP fanout requires the partner-network table and per-partner User rows.
        // For UAT we log; the producer-side wires land with PR T2.
        log.info("TenderPublished tenderId={} — fanout to LPs deferred until producer wiring lands.", e.tenderId());
    }

    /** Notify the owning TP_ADMIN when an LP submits a bid. */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onBidSubmitted(TenderEvents.BidSubmitted e) {
        log.info("BidSubmitted tenderId={} bidId={} — TP_ADMIN notification deferred until producer wiring lands.",
                e.tenderId(), e.bidId());
    }

    /** Winning LP gets SMS + WhatsApp + In-App; losing LPs get In-App from {@link #onBidRejected}. */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onTenderAwarded(TenderEvents.TenderAwarded e) {
        log.info("TenderAwarded tenderId={} winningPartnerId={} — LP notification deferred until producer wiring lands.",
                e.tenderId(), e.winningPartnerId());
    }

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onBidRejected(TenderEvents.BidRejected e) {
        log.info("BidRejected bidId={} — LP notification deferred until producer wiring lands.", e.bidId());
    }

    // ── User account lifecycle ─────────────────────────────────────────────────

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onTpAccountApproved(UserAccountEvents.TpAccountApproved e) {
        Map<String, String> params = Map.of(
                "tpName", e.tpName() == null ? "" : e.tpName(),
                "plan", e.plan() == null ? "" : e.plan());
        notificationService.dispatch(new NotificationDispatchRequest(
                "TpAccountApproved:" + e.tpAccountId(),
                e.tpAdminUserId(),
                List.of(Channel.SMS, Channel.IN_APP),
                "TP_ACCOUNT_APPROVED",
                params));
    }

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onTpAccountRejected(UserAccountEvents.TpAccountRejected e) {
        Map<String, String> params = Map.of("reason", e.reason() == null ? "" : e.reason());
        notificationService.dispatch(new NotificationDispatchRequest(
                "TpAccountRejected:" + e.tpAccountId(),
                e.tpAdminUserId(),
                List.of(Channel.SMS, Channel.IN_APP),
                "TP_ACCOUNT_REJECTED",
                params));
    }

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onTpPlanChanged(UserAccountEvents.TpPlanChanged e) {
        Map<String, String> params = Map.of(
                "oldPlan", e.oldPlan() == null ? "" : e.oldPlan(),
                "newPlan", e.newPlan() == null ? "" : e.newPlan());
        notificationService.dispatch(new NotificationDispatchRequest(
                "TpPlanChanged:" + e.tpAccountId() + ":" + e.newPlan(),
                e.tpAdminUserId(),
                List.of(Channel.SMS, Channel.IN_APP),
                "TP_PLAN_CHANGED",
                params));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Resolves a customer id to the corresponding {@code users.id}, if a User row exists.
     * Customers are keyed by phone in {@code users}; we look up the customer first to get
     * their phone, then resolve to a User. Returns empty for shadow customers or any
     * customer whose phone has not yet been activated as a portal user.
     */
    private Optional<String> resolveCustomerUserId(String customerId) {
        if (customerId == null) return Optional.empty();
        return customerRepository.findById(customerId)
                .map(Customer::getWhatsappPhone)
                .flatMap(userRepository::findByPhone)
                .map(com.cognilogistic.auth.model.User::getId);
    }

    /** Builds a parameter map with the order id and (optional) vehicle registration. */
    private static Map<String, String> orderParams(String orderId, String vehicleReg) {
        Map<String, String> p = new HashMap<>();
        p.put("orderId", orderId);
        if (vehicleReg != null) p.put("vehicleReg", vehicleReg);
        // customerName / tpName / reason are filled by callers when the event carries them.
        p.putIfAbsent("customerName", "");
        p.putIfAbsent("tpName", "");
        p.putIfAbsent("vehicleReg", "");
        return p;
    }
}
