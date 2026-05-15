package com.cognilogistic.order.service.portal;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.dto.OrderStatusLogDto;
import com.cognilogistic.order.dto.portal.PortalCreateOrderRequest;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.order.repository.OrderStatusLogRepository;
import com.cognilogistic.order.service.OrderDtoMapper;
import com.cognilogistic.order.service.OrderEvents;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for customer-portal order operations.
 *
 * <p>Differs from the TP-facing {@link com.cognilogistic.order.service.OrderService} in:
 * <ul>
 *   <li><strong>Scope:</strong> the customer only sees orders where {@code customer_id}
 *       matches their identity.</li>
 *   <li><strong>PII masking:</strong> driver name shows as "First L." (first + last
 *       initial); {@code internalNotes}, {@code freightCostInr}, {@code priceInr},
 *       and {@code driverDl} are nulled. Centralised in {@link OrderDtoMapper#toPortalDto}.</li>
 *   <li><strong>Read-only state machine:</strong> the portal cannot acknowledge,
 *       cancel, or modify orders — only list, view, view timeline, and create new.</li>
 * </ul>
 */
@Service
public class PortalOrderService {

    private static final Logger log = LoggerFactory.getLogger(PortalOrderService.class);

    private static final DateTimeFormatter ORDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    private final OrderRepository orders;
    private final OrderStatusLogRepository statusLog;
    private final CustomerRepository customers;
    private final ApplicationEventPublisher events;
    private final OrderDtoMapper dtoMapper;

    public PortalOrderService(OrderRepository orders,
                              OrderStatusLogRepository statusLog,
                              CustomerRepository customers,
                              ApplicationEventPublisher events,
                              OrderDtoMapper dtoMapper) {
        this.orders = orders;
        this.statusLog = statusLog;
        this.customers = customers;
        this.events = events;
        this.dtoMapper = dtoMapper;
    }

    @Transactional(readOnly = true)
    public List<OrderDto> listOrders(AuthPrincipal me) {
        Customer customer = loadCustomer(me);
        return orders.findAllByCustomerId(customer.getId()).stream()
                .map(o -> dtoMapper.toPortalDto(o, customer))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(AuthPrincipal me, String orderId) {
        Customer customer = loadCustomer(me);
        Order o = orders.findById(orderId).orElse(null);
        if (o == null) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        if (!o.getCustomerId().equals(customer.getId())) {
            log.warn("Portal order scope denied | orderId={} | callerCustomer={} | ownerCustomer={}",
                    suffix(orderId), suffix(customer.getId()), suffix(o.getCustomerId()));
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        return dtoMapper.toPortalDto(o, customer);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusLogDto> getTimeline(AuthPrincipal me, String orderId) {
        Customer customer = loadCustomer(me);
        Order o = orders.findById(orderId).orElse(null);
        if (o == null) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        if (!o.getCustomerId().equals(customer.getId())) {
            log.warn("Portal timeline scope denied | orderId={} | callerCustomer={} | ownerCustomer={}",
                    suffix(orderId), suffix(customer.getId()), suffix(o.getCustomerId()));
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        return statusLog.findByOrderIdOrderByTriggeredAtAsc(orderId).stream()
                .map(l -> new OrderStatusLogDto(l.getId(), l.getFromStatus(), l.getToStatus(),
                        l.getTriggeredByUserId(), l.getTriggeredAt(), l.getNote()))
                .toList();
    }

    /**
     * Creates a new order from the customer portal. The order is automatically
     * linked to the TP account that originally created the customer record
     * ({@code created_by_tp}). Logs the initial CREATED status transition (BR-06).
     *
     * <p>Aligned with the TP-create flow's field set so the FE can use the same
     * payload shape on both sides (BACKEND_GAPS §8). Customer-portal callers can
     * still send the legacy thin {@code pickup} / {@code drop} / {@code material}
     * fields; we coalesce.
     */
    @Transactional
    public OrderDto createOrder(AuthPrincipal me, PortalCreateOrderRequest req) {
        Customer customer = loadCustomer(me);

        String tpId = customer.getCreatedByTp();
        if (tpId == null || tpId.isBlank()) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Customer is not linked to a transport provider account");
        }

        String pickup = req.effectivePickupLocation();
        String drop = req.effectiveDropLocation();
        String goods = req.effectiveGoodsType();
        if (pickup == null || pickup.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pickupLocation is required",
                    Map.of("fields", Map.of("pickupLocation", "pickupLocation is required")));
        }
        if (drop == null || drop.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "dropLocation is required",
                    Map.of("fields", Map.of("dropLocation", "dropLocation is required")));
        }
        if (goods == null || goods.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "goodsType is required",
                    Map.of("fields", Map.of("goodsType", "goodsType is required")));
        }

        LocalDate pickupDate = req.pickupDate() != null ? req.pickupDate() : LocalDate.now(ZoneOffset.UTC);
        LocalDate expectedDelivery = req.expectedDeliveryDate();
        if (expectedDelivery != null && expectedDelivery.isBefore(pickupDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "expectedDeliveryDate must be on or after pickupDate",
                    Map.of("fields", Map.of("expectedDeliveryDate", "must be on or after pickupDate")));
        }

        BigDecimal wkg = req.effectiveWeightKg();
        if (wkg != null && wkg.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "weight must be positive",
                    Map.of("fields", Map.of("weightKg", "weight must be positive")));
        }
        if (req.volumeCbm() != null && req.volumeCbm().signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "volume must be positive",
                    Map.of("fields", Map.of("volumeCbm", "volume must be positive")));
        }

        // R8: PortalCreateOrderRequest now mirrors the TP-side CreateOrderRequest
        // (BACKEND_GAPS §8). Legacy thin fields (pickup/drop/material/weightTons)
        // are still accepted via the effective* helpers, which prefer the
        // canonical names when both are supplied.

        Order o = new Order();
        o.setTpAccountId(tpId);
        o.setCreatedByUserId(me.userId());
        o.setCustomerId(customer.getId());
        o.setOrderType(req.orderType());

        o.setGoodsType(goods.trim());
        o.setMaterialDescription(goods.trim()); // @Transient mirror so legacy reads still work

        o.setPickupLocation(pickup.trim());
        o.setDropLocation(drop.trim());
        // pickupDate is NOT NULL on the schema — default to today when the FE
        // omits it. Future iterations may surface a friendlier validation error.
        o.setPickupDate(pickupDate);
        o.setExpectedDeliveryDate(expectedDelivery);

        o.setWeightKg(req.effectiveWeightKg());
        o.setVolumeCbm(req.volumeCbm());
        o.setRequestedVehicleType(req.requestedVehicle());
        // priceInr is Integer (whole rupees) — schema column is INT. Default to 0
        // when not supplied so the NOT NULL DEFAULT 0 column gets a deterministic value.
        o.setPriceInr(req.priceInr() != null ? req.priceInr() : 0);

        // Customer denormalisation — snapshot for GR/LR rendering. The portal
        // path always uses the linked customer's name (no override on the body).
        o.setCustomerName(customer.getName());

        DeliveryType dt = req.effectiveDeliveryType();
        o.setDeliveryType(dt);
        o.setExpress(dt == DeliveryType.EXPRESS);

        o.setAssignedOfficeId(null);
        o.setStatus(OrderStatus.CREATED);
        o.setOrderNo(nextOrderNo(o.getTpAccountId(), LocalDate.now(ZoneOffset.UTC)));
        orders.save(o);

        writeLog(o.getId(), null, OrderStatus.CREATED, me.userId());
        events.publishEvent(new OrderEvents.OrderCreated(o.getId(), o.getTpAccountId(), o.getCustomerId(), o.getStatus()));

        log.info("Portal order created | id={} | orderNo={} | tp={} | customer={} | orderType={}",
                suffix(o.getId()), o.getOrderNo(), suffix(o.getTpAccountId()),
                suffix(customer.getId()), o.getOrderType());

        return dtoMapper.toPortalDto(o, customer);
    }

    private Customer loadCustomer(AuthPrincipal me) {
        return customers.findById(me.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Customer account not found"));
    }

    private void writeLog(String orderId, OrderStatus from, OrderStatus to, String userId) {
        var l = new com.cognilogistic.order.model.OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setTriggeredByUserId(userId);
        statusLog.save(l);
    }

    /**
     * Mirrors {@code OrderService#nextOrderNo} — see its Javadoc for the format
     * and race-tolerance rationale. Duplicated here rather than extracted into a
     * shared helper because both services compute the value at a single point in
     * their respective {@code create} flows.
     */
    private String nextOrderNo(String tpAccountId, LocalDate date) {
        String prefix = "COG-" + date.format(ORDER_NO_DATE_FORMAT) + "-";
        long existing = orders.countByTpAccountIdAndOrderNoStartingWith(tpAccountId, prefix);
        return prefix + String.format("%04d", existing + 1);
    }
}
