package com.cognilogistic.order.controller;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.order.dto.AcknowledgeRequest;
import com.cognilogistic.order.dto.CancelRequest;
import com.cognilogistic.order.dto.ConfirmFleetRequest;
import com.cognilogistic.order.dto.CreateOrderRequest;
import com.cognilogistic.order.dto.OrderDashboardDto;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.dto.OrderLotResponse;
import com.cognilogistic.order.dto.OrderStatusLogDto;
import com.cognilogistic.order.dto.ReassignRequest;
import com.cognilogistic.order.dto.UpdateOrderRequest;
import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.model.VehicleType;
import com.cognilogistic.order.service.GrLrDocumentService;
import com.cognilogistic.order.service.OrderDashboardService;
import com.cognilogistic.order.service.OrderService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * REST controller for the order lifecycle under {@code /api/v1/orders}.
 *
 * <p>Exposes CRUD and state-transition endpoints for TP-account users. The lifecycle
 * follows the V3.6 state machine:
 * <pre>CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED</pre>
 * {@code CANCELLED} is reachable from any pre-transit state (BR-01, BR-02).
 *
 * <p>The {@link OrderService.FleetConfirmGuard} is injected here and threaded through
 * {@code confirmFleet} to enforce the Vahan consent check (BR-10) without creating
 * a compile-time dependency from the order module onto the integration-client module.
 */
@Tag(name = "Orders (TP)", description = "TP order lifecycle, GR HTML. JWT required.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService service;
    private final OrderDashboardService dashboardService;
    private final OrderService.FleetConfirmGuard guard;
    private final GrLrDocumentService grLrService;

    public OrderController(OrderService service,
                           OrderDashboardService dashboardService,
                           OrderService.FleetConfirmGuard guard,
                           GrLrDocumentService grLrService) {
        this.service = service;
        this.dashboardService = dashboardService;
        this.guard = guard;
        this.grLrService = grLrService;
    }

    /**
     * Last-4-chars suffix for ids — mirrors {@code OfficeController.suffixId}. Used
     * everywhere we want to surface "which user / which tp" in logs without leaking
     * full UUIDs to log aggregators.
     */
    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    /**
     * Creates a new order in {@code CREATED} status (BR-04: shadow customer auto-created
     * if the customer phone is not yet registered).
     *
     * @param me  the authenticated TP user
     * @param req order creation parameters
     * @return the newly created order DTO
     */
    @PostMapping
    public ApiResponse<OrderDto> create(@CurrentUser AuthPrincipal me,
                                        @Valid @RequestBody CreateOrderRequest req) {
        log.info("[ENTRY] createOrder | userId={} | tp={} | orderType={} | companyIdPresent={} | phonePresent={} | customerNameLen={}",
                suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                req.orderType(),
                req.companyId() != null && !req.companyId().isBlank(),
                req.customerWhatsappPhone() != null && !req.customerWhatsappPhone().isBlank(),
                req.customerName() != null ? req.customerName().length() : 0);
        return ControllerRequestLogging.withExitLog(OrderController.class, "createOrder",
                () -> service.create(me, req));
    }

    /**
     * Lists orders for the caller's TP account with optional filtering.
     *
     * @param me       the authenticated TP user
     * @param status   optional status filter; {@code null} returns all statuses
     * @param officeId optional branch office filter
     * @param from     optional inclusive lower bound on {@code createdAt} (ISO-8601 instant)
     * @param to       optional exclusive upper bound on {@code createdAt} (ISO-8601 instant)
     * @return orders matching all supplied filters, ordered by {@code createdAt DESC}
     */
    @GetMapping
    public ApiResponse<List<OrderDto>> list(@CurrentUser AuthPrincipal me,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) String officeId,
                                            @RequestParam(required = false) DeliveryType deliveryType,
                                            @RequestParam(required = false) String requestedVehicleType,
                                            @RequestParam(required = false) String requestedVehicle,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDateFrom,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDateTo,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String vehicleFilter = requestedVehicleType != null && !requestedVehicleType.isBlank()
                ? requestedVehicleType
                : requestedVehicle;
        log.info("[ENTRY] listOrders | userId={} | tp={} | status={} | officeId={} | fromPresent={} | toPresent={}",
                suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                status, suffix(officeId), from != null, to != null);
        return ControllerRequestLogging.withExitLog(OrderController.class, "listOrders",
                () -> service.list(me, status, officeId, deliveryType, vehicleFilter,
                        pickupDateFrom, pickupDateTo, from, to));
    }

    /**
     * DB-backed dashboard aggregates for the orders summary (status pills, KPI cards,
     * express counts, 30-day conversion). Scoped to the caller's TP; optional filters
     * mirror the list view (office, delivery tier, vehicle type, pickup date range).
     */
    @GetMapping("/dashboard")
    public ApiResponse<OrderDashboardDto> dashboard(@CurrentUser AuthPrincipal me,
                                                    @RequestParam(required = false) String officeId,
                                                    @RequestParam(required = false) DeliveryType deliveryType,
                                                    @RequestParam(required = false) String requestedVehicleType,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDateFrom,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickupDateTo) {
        log.info("[ENTRY] orderDashboard | userId={} | tp={} | officeId={} | deliveryType={} | vehicleFilterPresent={} | pickupFromPresent={} | pickupToPresent={}",
                suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                suffix(officeId),
                deliveryType,
                requestedVehicleType != null && !requestedVehicleType.isBlank(),
                pickupDateFrom != null,
                pickupDateTo != null);
        return ControllerRequestLogging.withExitLog(OrderController.class, "orderDashboard",
                () -> dashboardService.dashboard(me, officeId, deliveryType, requestedVehicleType,
                        pickupDateFrom, pickupDateTo));
    }

    /**
     * Canonical labels for requested / assigned vehicle body types — identical to
     * {@link VehicleType} display names persisted in the database. Used by order UI dropdowns.
     */
    @GetMapping("/configuration/vehicle-types")
    public ApiResponse<List<String>> vehicleTypes() {
        List<String> labels = Arrays.stream(VehicleType.values()).map(VehicleType::getDisplayName).toList();
        log.debug("[EXIT] listVehicleTypes | count={}", labels.size());
        return ApiResponse.ok(labels);
    }

    /**
     * Retrieves a single order by ID, scoped to the caller's TP account.
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return the order DTO
     */
    @GetMapping("/{id}")
    public ApiResponse<OrderDto> get(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] getOrder | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "getOrder",
                () -> service.get(me, id));
    }

    /**
     * Returns the "connected lot" the order belongs to — every order in the
     * caller's TP that shares the same vehicle registration and pickup date.
     * Returns a singleton {@code orders} list when no other orders match (the
     * front-end suppresses the multi-order panel in that case).
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return the lot id + member orders ordered by {@code createdAt} ascending
     */
    @GetMapping("/{id}/lot")
    public ApiResponse<OrderLotResponse> lot(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] getOrderLot | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "getOrderLot",
                () -> service.getLot(me, id));
    }

    /**
     * Returns the full status-transition audit log for the order (BR-06).
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return list of status log entries ordered by timestamp ascending
     */
    @GetMapping("/{id}/history")
    public ApiResponse<List<OrderStatusLogDto>> history(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] getOrderHistory | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "getOrderHistory",
                () -> service.history(me, id));
    }

    /**
     * Partially updates editable order fields (BR-08: only allowed before FLEET_CONFIRMED).
     *
     * @param me  the authenticated TP user
     * @param id  the order ID to update
     * @param req fields to update; only non-null values are applied
     * @return the updated order DTO
     */
    @PatchMapping("/{id}")
    public ApiResponse<OrderDto> update(@CurrentUser AuthPrincipal me,
                                        @PathVariable String id,
                                        @Valid @RequestBody UpdateOrderRequest req) {
        log.info("[ENTRY] updateOrder | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "updateOrder",
                () -> service.update(me, id, req));
    }

    /**
     * Transitions an order from {@code CREATED} to {@code ACKNOWLEDGED}. Combined acknowledge
     * step: also sets the branch office if not already assigned (V3.6 — no separate ASSIGNED state).
     *
     * @param me  the authenticated TP user
     * @param id  the order ID
     * @param req optional body; {@code officeId} required only when the order has no assigned office
     * @return the updated order DTO
     */
    @PostMapping("/{id}/acknowledge")
    public ApiResponse<OrderDto> acknowledge(@CurrentUser AuthPrincipal me,
                                             @PathVariable String id,
                                             @Valid @RequestBody(required = false) AcknowledgeRequest req) {
        log.info("[ENTRY] acknowledgeOrder | id={} | userId={} | tp={} | officeIdPresent={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                req != null && req.officeId() != null && !req.officeId().isBlank());
        return ControllerRequestLogging.withExitLog(OrderController.class, "acknowledgeOrder",
                () -> service.acknowledge(me, id, req));
    }

    /**
     * Transitions an order from {@code ACKNOWLEDGED} to {@code FLEET_CONFIRMED}.
     * For FTL orders, vehicle registration is mandatory and Vahan consent must already exist (BR-09, BR-10).
     *
     * @param me    the authenticated TP user
     * @param id    the order ID
     * @param req   optional for PTL (pass null); required fields for FTL: vehicleRegistration, vahanConsentId
     * @return the updated order DTO
     */
    @PostMapping("/{id}/confirm-fleet")
    public ApiResponse<OrderDto> confirmFleet(@CurrentUser AuthPrincipal me,
                                              @PathVariable String id,
                                              @Valid @RequestBody(required = false) ConfirmFleetRequest req) {
        log.info("[ENTRY] confirmFleet | id={} | userId={} | tp={} | vehicleNumberPresent={} | driverPhonePresent={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                req != null && req.effectiveVehicleNumber() != null,
                req != null && req.driverPhone() != null && !req.driverPhone().isBlank());
        return ControllerRequestLogging.withExitLog(OrderController.class, "confirmFleet",
                () -> service.confirmFleet(me, id, req, guard));
    }

    /**
     * Transitions an order from {@code FLEET_CONFIRMED} to {@code IN_TRANSIT}.
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return the updated order DTO
     */
    @PostMapping("/{id}/start-transit")
    public ApiResponse<OrderDto> startTransit(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] startTransit | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "startTransit",
                () -> service.startTransit(me, id));
    }

    /**
     * Transitions an order from {@code IN_TRANSIT} to {@code DELIVERED}.
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return the updated order DTO
     */
    @PostMapping("/{id}/deliver")
    public ApiResponse<OrderDto> deliver(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] deliverOrder | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLog(OrderController.class, "deliverOrder",
                () -> service.deliver(me, id));
    }

    /**
     * Cancels an order (BR-02: not allowed once {@code IN_TRANSIT} or {@code DELIVERED}).
     *
     * @param me  the authenticated TP user
     * @param id  the order ID
     * @param req optional body; cancellation reason is stored in {@code cancelled_reason} for audit
     * @return the updated order DTO
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDto> cancel(@CurrentUser AuthPrincipal me,
                                        @PathVariable String id,
                                        @Valid @RequestBody(required = false) CancelRequest req) {
        log.info("[ENTRY] cancelOrder | id={} | userId={} | tp={} | reasonPresent={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null),
                req != null && req.reason() != null && !req.reason().isBlank());
        return ControllerRequestLogging.withExitLog(OrderController.class, "cancelOrder",
                () -> service.cancel(me, id, req == null ? null : req.reason()));
    }

    /**
     * Reassigns an order to a different branch office (BR-05: only TP_ADMIN can reassign;
     * V3.6: office change is an attribute operation — no status transition occurs).
     *
     * @param me  the authenticated TP user (must be TP_ADMIN role)
     * @param id  the order ID
     * @param req the target office ID
     * @return the updated order DTO
     */
    @PostMapping("/{id}/reassign")
    public ApiResponse<OrderDto> reassign(@CurrentUser AuthPrincipal me,
                                          @PathVariable String id,
                                          @Valid @RequestBody ReassignRequest req) {
        log.info("[ENTRY] reassignOrder | id={} | userId={} | tp={} | newOfficeId={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null), suffix(req.newOfficeId()));
        return ControllerRequestLogging.withExitLog(OrderController.class, "reassignOrder",
                () -> service.reassign(me, id, req.newOfficeId()));
    }

    /**
     * Generates and returns an HTML Goods Receipt (GR) document for the order.
     * Available once the order is in {@code ACKNOWLEDGED} status or later.
     *
     * @param me the authenticated TP user
     * @param id the order ID
     * @return the GR as an HTML string (content-type: text/html)
     */
    @GetMapping(value = "/{id}/gr", produces = MediaType.TEXT_HTML_VALUE)
    public String generateGr(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] generateGr | id={} | userId={} | tp={}",
                suffix(id), suffix(me != null ? me.userId() : null),
                suffix(me != null ? me.tpAccountId() : null));
        return ControllerRequestLogging.withExitLogValue(OrderController.class, "generateGr",
                () -> grLrService.generateGrHtml(me.tpAccountId(), id));
    }
}
