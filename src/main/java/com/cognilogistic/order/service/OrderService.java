package com.cognilogistic.order.service;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.support.AuthPhoneNormalizer;
import com.cognilogistic.order.dto.*;
import com.cognilogistic.order.model.*;
import com.cognilogistic.order.repository.*;
import com.cognilogistic.order.statemachine.OrderStateMachine;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.model.Office;
import com.cognilogistic.user.repository.OfficeRepository;
import com.cognilogistic.user.repository.UserOfficeAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Core service orchestrating all order lifecycle operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Order creation with shadow-customer auto-creation (BR-04) and server-side
 *       generation of the human-readable {@code orderNo} (e.g. {@code COG-20260508-0001}).</li>
 *   <li>State transitions validated by {@link OrderStateMachine} (BR-01, BR-02).</li>
 *   <li>Audit logging of every status change, including the initial CREATED row (BR-06).</li>
 *   <li>Domain event publication via Spring's {@link ApplicationEventPublisher}.</li>
 *   <li>Enforcement of FTL vehicle requirements (BR-09) and Vahan consent checks (BR-10)
 *       via the {@link FleetConfirmGuard}. The Vahan consent gate can be relaxed for UAT
 *       demos via the {@code orders.fleet.require-vahan-consent} property
 *       (default {@code true}).</li>
 *   <li>Editability guard (BR-08: no edits after FLEET_CONFIRMED).</li>
 *   <li>Reassignment privilege check (BR-05: TP_ADMIN only).</li>
 *   <li>Resolution of front-end's {@code office} (display-name) update field to
 *       the canonical {@code assignedOfficeId}.</li>
 * </ul>
 *
 * <p><strong>R1 reconciliation note.</strong> Several DTO fields exist in two
 * spellings (legacy + canonical) so the front-end can switch over field-by-field.
 * The service prefers the canonical name when both are supplied — see the
 * {@code effective*} helpers on each request DTO.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Format string for the dated portion of {@code order_no}. */
    private static final DateTimeFormatter ORDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern PHONE_WIRE = Pattern.compile("^\\+?\\d{10,15}$");

    /** Last 4 chars of an id for log lines — mirrors {@code OfficeService}. */
    private static String suffix(String id) {
        if (id == null || id.length() < 4) return "****";
        return "****" + id.substring(id.length() - 4);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private final OrderRepository orders;
    private final OrderStatusLogRepository statusLog;
    private final CustomerService customers;
    private final CompanyRepository companies;
    private final OfficeRepository offices;
    private final UserOfficeAssignmentRepository userOffices;
    private final OrderAccessScope accessScope;
    private final OrderStateMachine machine;
    private final ApplicationEventPublisher events;
    private final OrderDtoMapper dtoMapper;

    /**
     * Property toggle for the FTL Vahan-consent gate (BR-10). Default {@code true}.
     * Set to {@code false} on the UAT profile so the front-end can drive the
     * fleet-confirmation flow without going through {@code /vahan/*}. The real
     * pilot DB always runs with the gate on.
     */
    private final boolean requireVahanConsent;

    public OrderService(OrderRepository orders,
                        OrderStatusLogRepository statusLog,
                        CustomerService customers,
                        CompanyRepository companies,
                        OfficeRepository offices,
                        UserOfficeAssignmentRepository userOffices,
                        OrderAccessScope accessScope,
                        OrderStateMachine machine,
                        ApplicationEventPublisher events,
                        OrderDtoMapper dtoMapper,
                        @Value("${orders.fleet.require-vahan-consent:true}") boolean requireVahanConsent) {
        this.orders = orders;
        this.statusLog = statusLog;
        this.customers = customers;
        this.companies = companies;
        this.offices = offices;
        this.userOffices = userOffices;
        this.accessScope = accessScope;
        this.machine = machine;
        this.events = events;
        this.dtoMapper = dtoMapper;
        this.requireVahanConsent = requireVahanConsent;
    }

    // ===== Create =====

    /**
     * Creates a new order in {@code CREATED} status. Auto-creates a shadow customer
     * if the supplied phone number has no existing customer record (BR-04). Generates
     * the {@code order_no} server-side. Logs the initial CREATED transition with
     * {@code from_status = NULL} (BR-06) and publishes {@link OrderEvents.OrderCreated}.
     *
     * @param me  the authenticated TP user
     * @param req order creation parameters (canonical or legacy aliases — service coalesces)
     * @return the created order DTO
     */
    @Transactional
    public OrderDto create(AuthPrincipal me, CreateOrderRequest req) {
        requireTpAccount(me);

        String goods = req.effectiveGoodsType();
        if (goods == null || goods.isBlank()) {
            // Use details.fields shape (not details.field) so the FE form-error mapper
            // attaches the message to the `goodsType` input. Matches the FE Zod
            // contract on CreateOrderPage and mirrors GlobalExceptionHandler's
            // bean-validation shape.
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "goodsType is required",
                    Map.of("fields", Map.of("goodsType", "goodsType is required")));
        }

        if (req.expectedDeliveryDate() != null
                && req.pickupDate() != null
                && req.expectedDeliveryDate().isBefore(req.pickupDate())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "expectedDeliveryDate must be on or after pickupDate",
                    Map.of("fields", Map.of(
                            "expectedDeliveryDate",
                            "must be on or after pickupDate")));
        }

        String resolvedCompanyId = null;
        if (req.companyId() != null && !req.companyId().isBlank()) {
            // Tenant scoping: even with a valid UUID, only companies owned by the
            // caller's TP are accepted. Active-company gate keeps deactivated rows
            // out of new orders.
            Company co = companies.findById(req.companyId().trim())
                    .filter(c -> c.getTpAccountId().equals(me.tpAccountId()))
                    .filter(Company::isActive)
                    .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND,
                            "Company not found",
                            Map.of("fields", Map.of("companyId", "company not found in this TP"))));
            resolvedCompanyId = co.getId();
        }

        String phoneInput = req.customerWhatsappPhone();
        if (phoneInput != null && phoneInput.isBlank()) {
            phoneInput = null;
        }
        String effectivePhone = phoneInput != null ? AuthPhoneNormalizer.normalize(phoneInput) : null;
        if (effectivePhone != null && effectivePhone.isBlank()) {
            effectivePhone = null;
        }

        if ((effectivePhone == null || effectivePhone.isBlank()) && resolvedCompanyId != null) {
            Company co = companies.findById(resolvedCompanyId).orElseThrow();
            String companyPhone = co.getContactPhone();
            if (companyPhone != null && !companyPhone.isBlank()) {
                effectivePhone = AuthPhoneNormalizer.normalize(companyPhone);
            }
        }

        // Phone is OPTIONAL at create time (UAT decision): when neither the form
        // nor the company master supplies one, the order is created with a
        // phone-less shadow customer. Format validation still applies when the
        // caller did send a value — we just don't require its presence.
        if (effectivePhone != null && !effectivePhone.isBlank()
                && !PHONE_WIRE.matcher(effectivePhone).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid WhatsApp phone format",
                    Map.of("fields", Map.of("customerWhatsappPhone", "phone must be 10-15 digits")));
        }

        // BR-04: shadow customer on create. Phone may be null — service handles that.
        Customer customer = customers.findOrCreateShadow(effectivePhone, me.tpAccountId(), req.customerName());

        Order o = new Order();
        o.setTpAccountId(me.tpAccountId());
        o.setCreatedByUserId(me.userId());
        o.setCustomerId(customer.getId());
        o.setCompanyId(resolvedCompanyId);
        o.setOrderType(req.orderType());

        // Canonical fields — pulled via the DTO's effective* helpers so the legacy
        // aliases (materialDescription / weightTons / priceInr / express) keep working.
        o.setGoodsType(goods.trim());
        o.setMaterialDescription(goods.trim()); // @Transient — kept in sync for legacy reads
        o.setWeightKg(req.effectiveWeightKg());
        o.setVolumeCbm(req.volumeCbm());
        o.setRequestedVehicleType(req.requestedVehicle());
        // priceInr is Integer (whole rupees) — schema column is INT NOT NULL DEFAULT 0.
        // Default to 0 when caller omits both the canonical and legacy field.
        o.setPriceInr(req.effectiveFreightCostInr() != null ? req.effectiveFreightCostInr() : 0);
        o.setInternalNotes(req.internalNotes());

        // Customer denormalisation — snapshot at create time so GR/LR documents keep
        // rendering the right name even if the customer record is later edited.
        o.setCustomerName(req.customerName().trim());
        String gstinSnap = req.customerGstin();
        if (gstinSnap != null && gstinSnap.isBlank()) {
            gstinSnap = null;
        } else if (gstinSnap != null) {
            gstinSnap = gstinSnap.trim().toUpperCase();
        }
        o.setCustomerGstin(gstinSnap);

        // Locations + dates — required NOT NULL on the orders table per schema v5.0.
        o.setPickupLocation(req.pickupLocation().trim());
        o.setDropLocation(req.dropLocation().trim());
        o.setPickupDate(req.pickupDate());
        o.setExpectedDeliveryDate(req.expectedDeliveryDate());

        // Delivery type + express flag — kept in sync to avoid drift between the two
        // representations of the same boolean.
        DeliveryType dt = req.effectiveDeliveryType();
        o.setDeliveryType(dt);
        o.setExpress(req.effectiveExpress());

        o.setAssignedOfficeId(null); // always null at creation — set via /acknowledge
        o.setStatus(OrderStatus.CREATED);
        o.setOrderNo(nextOrderNo(me.tpAccountId(), LocalDate.now(ZoneOffset.UTC)));

        orders.save(o);

        // BR-06: log even initial CREATED with from_status NULL.
        writeLog(o.getId(), null, OrderStatus.CREATED, me.userId(), null);
        events.publishEvent(new OrderEvents.OrderCreated(o.getId(), o.getTpAccountId(), o.getCustomerId(), o.getStatus()));

        log.info("Order created | id={} | orderNo={} | tp={} | createdBy={} | companyIdPresent={} | orderType={}",
                suffix(o.getId()), o.getOrderNo(), suffix(o.getTpAccountId()),
                suffix(o.getCreatedByUserId()), resolvedCompanyId != null, o.getOrderType());

        return dtoMapper.toDto(o, customer);
    }

    /**
     * Returns a filtered list of orders for the caller's TP account.
     */
    @Transactional(readOnly = true)
    public List<OrderDto> list(AuthPrincipal me,
                               OrderStatus status,
                               String officeIdRaw,
                               DeliveryType deliveryType,
                               String requestedVehicleType,
                               LocalDate pickupDateFrom,
                               LocalDate pickupDateTo,
                               Instant from,
                               Instant to) {
        requireTpAccount(me);
        String officeId = accessScope.resolveOfficeFilter(me, officeIdRaw, me.tpAccountId());
        List<String> assignedOnly = accessScope.officeIdsForQuery(me, officeId);
        boolean restrict = assignedOnly != null;
        if (restrict && assignedOnly.isEmpty()) {
            return List.of();
        }
        return orders.search(
                        me.tpAccountId(),
                        status,
                        officeId,
                        restrict,
                        restrict ? assignedOnly : List.of(),
                        deliveryType,
                        blankToNull(requestedVehicleType),
                        pickupDateFrom,
                        pickupDateTo,
                        from,
                        to).stream()
                .map(o -> dtoMapper.toDto(o, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(AuthPrincipal me, String id) {
        Order o = loadOrderForTp(me, id);
        accessScope.requireReadableOrder(me, o);
        Customer c = customers.findById(o.getCustomerId()).orElse(null);
        return dtoMapper.toDto(o, c);
    }

    /**
     * Returns the "connected lot" for an order — every order in the same TP that
     * shares the target's vehicle registration AND pickup date. Returns a
     * singleton list when no other orders match (so the FE can render the
     * order-detail page even before fleet confirmation).
     */
    @Transactional(readOnly = true)
    public OrderLotResponse getLot(AuthPrincipal me, String id) {
        Order o = loadOrderForTp(me, id);
        accessScope.requireReadableOrder(me, o);
        List<Order> lot;
        String lotId;
        if (o.getVehicleRegistration() != null
                && !o.getVehicleRegistration().isBlank()
                && o.getPickupDate() != null) {
            lot = orders.findLot(me.tpAccountId(), o.getVehicleRegistration(), o.getPickupDate());
            if (lot.isEmpty()) {
                lot = List.of(o);
            }
            lotId = "lot-" + o.getVehicleRegistration() + "-" + o.getPickupDate();
        } else {
            lot = List.of(o);
            lotId = o.getId();
        }
        List<OrderDto> dtos = lot.stream().map(x -> dtoMapper.toDto(x, null)).toList();
        return new OrderLotResponse(lotId, dtos);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusLogDto> history(AuthPrincipal me, String id) {
        loadOrderForTp(me, id);
        return statusLog.findByOrderIdOrderByTriggeredAtAsc(id).stream()
                .map(l -> new OrderStatusLogDto(l.getId(), l.getFromStatus(), l.getToStatus(),
                        l.getTriggeredByUserId(), l.getTriggeredAt(), l.getNote()))
                .toList();
    }

    // ===== Edit (BR-08) =====

    /**
     * Partially updates editable order fields. Only non-null values in the request are applied.
     *
     * <p>Aliases handled here:
     * <ul>
     *   <li>{@code office} (display name) → resolved to {@code assignedOfficeId} via
     *       {@link OfficeRepository}; ambiguous matches return {@code OFFICE_NOT_FOUND}.</li>
     *   <li>{@code freightCostInr} ↔ {@code priceInr} — service prefers the canonical name.</li>
     *   <li>{@code goodsType} ↔ {@code materialDescription}.</li>
     *   <li>{@code vehicleNumber} → {@code vehicleRegistration} (uppercased).</li>
     *   <li>{@code assignedVehicle} (display string) → {@link VehicleType} enum.</li>
     * </ul>
     */
    @Transactional
    public OrderDto update(AuthPrincipal me, String id, UpdateOrderRequest req) {
        Order o = loadOrderForTp(me, id);
        requireEditable(o);

        if (o.getAssignedOfficeId() != null) {
            requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());
        }

        log.info("Order update scan | id={} | tp={} | userId={} | status={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()), o.getStatus());

        // Only re-link customer when a non-blank phone is supplied. Blank string must not
        // route through findOrCreateShadow(null, …) — that would mint a new phone-less
        // shadow row on every PATCH (orphan customers).
        if (req.customerWhatsappPhone() != null && !req.customerWhatsappPhone().isBlank()) {
            String norm = AuthPhoneNormalizer.normalize(req.customerWhatsappPhone().trim());
            if (norm.isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid WhatsApp phone format",
                        Map.of("fields", Map.of("customerWhatsappPhone", "phone must be 10-15 digits")));
            }
            if (!PHONE_WIRE.matcher(norm).matches()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid WhatsApp phone format",
                        Map.of("fields", Map.of("customerWhatsappPhone", "phone must be 10-15 digits")));
            }
            Customer updated = customers.findOrCreateShadow(norm, me.tpAccountId(), o.getCustomerName());
            o.setCustomerId(updated.getId());
        }
        if (req.effectiveGoodsType() != null) {
            o.setGoodsType(req.effectiveGoodsType());
            o.setMaterialDescription(req.effectiveGoodsType()); // @Transient mirror
        }
        if (req.weightKg() != null) o.setWeightKg(req.weightKg());
        if (req.volumeCbm() != null) o.setVolumeCbm(req.volumeCbm());
        if (req.requestedVehicle() != null) o.setRequestedVehicleType(req.requestedVehicle());
        if (req.effectiveFreightCostInr() != null) o.setPriceInr(req.effectiveFreightCostInr());
        if (req.internalNotes() != null) o.setInternalNotes(req.internalNotes());

        Boolean express = req.effectiveExpress();
        if (express != null) {
            o.setExpress(express);
            o.setDeliveryType(express ? DeliveryType.EXPRESS : DeliveryType.NORMAL);
        }

        // Office: explicit id wins over name-resolution to keep things deterministic.
        if (req.assignedOfficeId() != null) {
            requireOfficeBelongsToTp(req.assignedOfficeId(), me.tpAccountId());
            requireBranchOrPrimaryOfOffice(me, req.assignedOfficeId());
            o.setAssignedOfficeId(req.assignedOfficeId());
        } else if (req.office() != null) {
            String oid = resolveOfficeIdByName(req.office(), me.tpAccountId());
            requireBranchOrPrimaryOfOffice(me, oid);
            o.setAssignedOfficeId(oid);
        }

        // Vehicle aliases — these are normally only set at FLEET_CONFIRMED, but the
        // FE's PATCH payload includes them so we accept here too.
        if (req.vehicleNumber() != null) o.setVehicleRegistration(req.vehicleNumber().toUpperCase());
        if (req.assignedVehicle() != null) {
            VehicleType vt = parseVehicleType(req.assignedVehicle());
            if (vt != null) o.setVehicleType(vt);
        }

        if (req.pickupLocation() != null) o.setPickupLocation(req.pickupLocation());
        if (req.dropLocation() != null) o.setDropLocation(req.dropLocation());
        if (req.pickupDate() != null) o.setPickupDate(req.pickupDate());
        if (req.expectedDeliveryDate() != null) o.setExpectedDeliveryDate(req.expectedDeliveryDate());

        log.info("Order updated | id={} | tp={} | userId={} | status={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()), o.getStatus());
        return dtoMapper.toDto(o, null);
    }

    private void requireEditable(Order o) {
        if (o.getStatus() == OrderStatus.FLEET_CONFIRMED
                || o.getStatus() == OrderStatus.IN_TRANSIT
                || o.getStatus() == OrderStatus.DELIVERED
                || o.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Order is no longer editable in status " + o.getStatus());
        }
    }

    // ===== Transitions =====

    @Transactional
    public OrderDto acknowledge(AuthPrincipal me, String id, AcknowledgeRequest req) {
        Order o = loadOrderForTp(me, id);
        log.info("Acknowledge scan | id={} | tp={} | userId={} | currentStatus={} | officeAlreadySet={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()),
                o.getStatus(), o.getAssignedOfficeId() != null);

        if (o.getAssignedOfficeId() == null) {
            if (req == null || req.officeId() == null || req.officeId().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "officeId is required when order has no assigned office",
                        Map.of("fields", Map.of("officeId", "officeId is required")));
            }
            requireOfficeBelongsToTp(req.officeId(), me.tpAccountId());
            o.setAssignedOfficeId(req.officeId());
        }

        requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());

        machine.requireTransition(o.getStatus(), OrderStatus.ACKNOWLEDGED);
        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.ACKNOWLEDGED);
        writeLog(id, from, OrderStatus.ACKNOWLEDGED, me.userId(), null);
        events.publishEvent(new OrderEvents.OrderStatusChanged(id, from, OrderStatus.ACKNOWLEDGED, me.userId()));
        log.info("Order transitioned | id={} | tp={} | {} -> ACKNOWLEDGED | officeAssigned={}",
                suffix(id), suffix(me.tpAccountId()), from, o.getAssignedOfficeId() != null);
        return dtoMapper.toDto(o, null);
    }

    /**
     * Transitions an order from {@code ACKNOWLEDGED} to {@code FLEET_CONFIRMED}.
     * For FTL: validates vehicle registration (BR-09) and — when
     * {@code orders.fleet.require-vahan-consent=true} — delegates Vahan consent
     * check to the {@link FleetConfirmGuard} (BR-10).
     */
    @Transactional
    public OrderDto confirmFleet(AuthPrincipal me, String id, ConfirmFleetRequest req, FleetConfirmGuard guard) {
        Order o = loadOrderForTp(me, id);
        log.info("ConfirmFleet scan | id={} | tp={} | userId={} | status={} | orderType={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()),
                o.getStatus(), o.getOrderType());
        requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());
        machine.requireTransition(o.getStatus(), OrderStatus.FLEET_CONFIRMED);

        // Driver name required for both FTL and PTL (BR-FLT-02).
        if (req == null || req.driverName() == null || req.driverName().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Driver name is required for fleet confirmation",
                    Map.of("fields", Map.of("driverName", "driverName is required")));
        }

        String vehicleNo = req.effectiveVehicleNumber();
        if (o.getOrderType() == OrderType.FTL) {
            if (vehicleNo == null || vehicleNo.isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Vehicle number required for FTL orders",
                        Map.of("fields", Map.of("vehicleNumber", "vehicleNumber is required for FTL")));
            }
            // BR-10 Vahan consent gate — toggleable per environment. UAT runs with the
            // gate off so the FE can demo without going through /vahan/*; pilot keeps
            // it on so the audit trail is preserved.
            if (requireVahanConsent) {
                guard.requireVahanConsent(id, vehicleNo);
            }
        }

        if (o.getOrderType() == OrderType.FTL) {
            o.setVehicleRegistration(vehicleNo != null ? vehicleNo.toUpperCase() : null);
        } else if (vehicleNo != null && !vehicleNo.isBlank()) {
            o.setVehicleRegistration(vehicleNo.toUpperCase());
        }
        o.setDriverName(req.driverName().trim());
        if (req.driverPhone() != null && !req.driverPhone().isBlank()) {
            o.setDriverPhone(req.driverPhone().trim());
        }
        if (req.driverDl() != null && !req.driverDl().isBlank()) {
            o.setDriverDl(req.driverDl().trim().toUpperCase());
        }
        VehicleType vt = req.effectiveVehicleType();
        if (vt != null) {
            o.setVehicleType(vt);
        }

        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.FLEET_CONFIRMED);
        writeLog(id, from, OrderStatus.FLEET_CONFIRMED, me.userId(), null);
        events.publishEvent(new OrderEvents.OrderStatusChanged(id, from, OrderStatus.FLEET_CONFIRMED, me.userId()));
        log.info("Order transitioned | id={} | tp={} | {} -> FLEET_CONFIRMED | orderType={}",
                suffix(id), suffix(me.tpAccountId()), from, o.getOrderType());
        return dtoMapper.toDto(o, null);
    }

    @Transactional
    public OrderDto startTransit(AuthPrincipal me, String id) {
        Order o = loadOrderForTp(me, id);
        log.info("StartTransit scan | id={} | tp={} | userId={} | status={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()), o.getStatus());
        requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());
        machine.requireTransition(o.getStatus(), OrderStatus.IN_TRANSIT);
        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.IN_TRANSIT);
        writeLog(id, from, OrderStatus.IN_TRANSIT, me.userId(), null);
        events.publishEvent(new OrderEvents.OrderStatusChanged(id, from, OrderStatus.IN_TRANSIT, me.userId()));
        log.info("Order transitioned | id={} | tp={} | {} -> IN_TRANSIT",
                suffix(id), suffix(me.tpAccountId()), from);
        return dtoMapper.toDto(o, null);
    }

    @Transactional
    public OrderDto deliver(AuthPrincipal me, String id) {
        Order o = loadOrderForTp(me, id);
        log.info("Deliver scan | id={} | tp={} | userId={} | status={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()), o.getStatus());
        requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());
        machine.requireTransition(o.getStatus(), OrderStatus.DELIVERED);
        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.DELIVERED);
        writeLog(id, from, OrderStatus.DELIVERED, me.userId(), null);
        events.publishEvent(new OrderEvents.OrderStatusChanged(id, from, OrderStatus.DELIVERED, me.userId()));
        log.info("Order transitioned | id={} | tp={} | {} -> DELIVERED",
                suffix(id), suffix(me.tpAccountId()), from);
        return dtoMapper.toDto(o, null);
    }

    @Transactional
    public OrderDto cancel(AuthPrincipal me, String id, String reason) {
        Order o = loadOrderForTp(me, id);
        log.info("Cancel scan | id={} | tp={} | userId={} | status={} | reasonPresent={}",
                suffix(id), suffix(me.tpAccountId()), suffix(me.userId()),
                o.getStatus(), reason != null && !reason.isBlank());
        requireBranchOrPrimaryOfOffice(me, o.getAssignedOfficeId());
        machine.requireTransition(o.getStatus(), OrderStatus.CANCELLED);
        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelledReason(reason);
        writeLog(id, from, OrderStatus.CANCELLED, me.userId(), reason);
        events.publishEvent(new OrderEvents.OrderStatusChanged(id, from, OrderStatus.CANCELLED, me.userId()));
        log.info("Order transitioned | id={} | tp={} | {} -> CANCELLED | reasonPresent={}",
                suffix(id), suffix(me.tpAccountId()), from, reason != null && !reason.isBlank());
        return dtoMapper.toDto(o, null);
    }

    @Transactional
    public OrderDto reassign(AuthPrincipal me, String id, String newOfficeId) {
        if (!me.isPrimary()) {
            log.warn("Reassign denied (role) | userId={} | tp={} | id={} | role={}",
                    suffix(me.userId()), suffix(me.tpAccountId()), suffix(id), me.role());
            throw new ApiException(ErrorCode.FORBIDDEN, "Only TP_ADMIN can reassign orders");
        }
        Order o = loadOrderForTp(me, id);
        if (o.getStatus() == OrderStatus.FLEET_CONFIRMED
                || o.getStatus() == OrderStatus.IN_TRANSIT
                || o.getStatus() == OrderStatus.DELIVERED
                || o.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Cannot reassign order in status " + o.getStatus());
        }
        requireOfficeBelongsToTp(newOfficeId, me.tpAccountId());
        o.setAssignedOfficeId(newOfficeId);
        log.info("Order reassigned | id={} | tp={} | newOfficeId={} | by={}",
                suffix(id), suffix(me.tpAccountId()), suffix(newOfficeId), suffix(me.userId()));
        return dtoMapper.toDto(o, null);
    }

    // ===== Helpers =====

    private void requireTpAccount(AuthPrincipal me) {
        if (me == null || me.tpAccountId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not associated with a TP account");
        }
    }

    private static void requirePresent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    fieldName + " is required",
                    Map.of("field", fieldName));
        }
    }

    private Order loadOrderForTp(AuthPrincipal me, String id) {
        requireTpAccount(me);
        Order o = orders.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));
        if (!o.getTpAccountId().equals(me.tpAccountId())) {
            // IDOR-resistance: log the cross-tenant attempt with masked ids so SOC can
            // pattern-match on it. Surface as ORDER_NOT_FOUND (not FORBIDDEN) so the
            // caller can't enumerate which order ids exist on other TPs.
            log.warn("Cross-tenant order access blocked | callerTp={} | ownerTp={} | id={} | userId={}",
                    suffix(me.tpAccountId()), suffix(o.getTpAccountId()),
                    suffix(id), suffix(me.userId()));
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        return o;
    }

    private void requireOfficeBelongsToTp(String officeId, String tpId) {
        offices.findById(officeId)
                .filter(o -> o.getTpAccountId().equals(tpId))
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Office does not belong to your TP account"));
    }

    /**
     * Resolves a branch-office display name to its UUID for the caller's TP account.
     * Throws {@code OFFICE_NOT_FOUND} when no match (or multiple matches) exist —
     * we want the FE to send the canonical id whenever possible.
     */
    private String resolveOfficeIdByName(String officeName, String tpAccountId) {
        if (officeName == null || officeName.isBlank()) {
            return null;
        }
        List<Office> candidates = offices.findAll().stream()
                .filter(o -> tpAccountId.equals(o.getTpAccountId()))
                .filter(o -> officeName.equalsIgnoreCase(o.getName()))
                .toList();
        if (candidates.size() != 1) {
            throw new ApiException(ErrorCode.OFFICE_NOT_FOUND,
                    "No unique office matches name: " + officeName,
                    Map.of("office", officeName, "matches", candidates.size()));
        }
        return candidates.get(0).getId();
    }

    private void requireBranchOrPrimaryOfOffice(AuthPrincipal me, String officeId) {
        if (officeId == null) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "Order has no assigned office");
        }
        if (me.isPrimary()) {
            requireOfficeBelongsToTp(officeId, me.tpAccountId());
            return;
        }
        if (!userOffices.existsByUserIdAndOfficeId(me.userId(), officeId)) {
            log.warn("Office scope denied | userId={} | tp={} | officeId={} | role={}",
                    suffix(me.userId()), suffix(me.tpAccountId()), suffix(officeId), me.role());
            throw new ApiException(ErrorCode.FORBIDDEN, "User not assigned to this office");
        }
    }

    private void writeLog(String orderId, OrderStatus from, OrderStatus to, String userId, String note) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setTriggeredByUserId(userId);
        l.setNote(note);
        statusLog.save(l);
    }

    /**
     * Generates the next {@code order_no} for a TP on a given date.
     *
     * <p>Format: {@code COG-YYYYMMDD-NNNN} where {@code NNNN} is the 1-based count
     * of orders the TP has created on that date, zero-padded to 4 digits.
     *
     * <p>Race tolerance: under concurrent creates, two callers can pick the same
     * sequence. The DB-level UNIQUE on {@code (tp_account_id, order_no)} rejects
     * the loser; the caller retries (or sees a 5xx in UAT — acceptable scale).
     */
    private String nextOrderNo(String tpAccountId, LocalDate date) {
        String prefix = "COG-" + date.format(ORDER_NO_DATE_FORMAT) + "-";
        long existing = orders.countByTpAccountIdAndOrderNoStartingWith(tpAccountId, prefix);
        return prefix + String.format("%04d", existing + 1);
    }

    @Transactional(readOnly = true)
    public List<Order> findAllByIds(List<String> ids, String tpAccountId) {
        return orders.findAllById(ids).stream()
                .filter(o -> o.getTpAccountId().equals(tpAccountId))
                .toList();
    }

    /**
     * Parses a {@link VehicleType} from a display-name string like {@code "14 ft"}.
     * Returns {@code null} for unknown values — caller decides whether to error.
     */
    private static VehicleType parseVehicleType(String displayName) {
        if (displayName == null) return null;
        for (VehicleType vt : VehicleType.values()) {
            if (vt.getDisplayName().equalsIgnoreCase(displayName)) return vt;
        }
        return null;
    }

    /**
     * Allows the integration-client module to plug in BR-10 enforcement without an upward
     * dependency from order → integrationclient.
     */
    public interface FleetConfirmGuard {
        void requireVahanConsent(String orderId, String vehicleRegistration);
        /** No-op for tests / CI environments where the integrationclient is absent. */
        FleetConfirmGuard NOOP = (o, v) -> {};
    }
}
