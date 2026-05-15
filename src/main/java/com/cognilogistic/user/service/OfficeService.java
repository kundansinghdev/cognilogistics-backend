package com.cognilogistic.user.service;

import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.dto.CreateOfficeRequest;
import com.cognilogistic.user.dto.OfficeOrderMetricsDto;
import com.cognilogistic.user.dto.OfficeResponseDto;
import com.cognilogistic.user.dto.UpdateOfficeRequest;
import com.cognilogistic.user.model.Office;
import com.cognilogistic.user.repository.OfficeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for branch office CRUD operations, scoped to a TP account.
 *
 * <p>Enforces all branch-office business rules defined in CLAUDE.md §8:
 * <ul>
 *   <li>BR-OFF-01 — {@code name}, {@code code}, {@code city}, {@code state} are mandatory on create.</li>
 *   <li>BR-OFF-02 — {@code code} must be unique within a {@code tp_account_id}.</li>
 *   <li>BR-OFF-03 — {@code gstin}, if provided, must match the 15-char Indian GSTIN regex.</li>
 *   <li>BR-OFF-04 — offices with order history must never be hard-deleted; use soft-delete instead.</li>
 *   <li>BR-OFF-06 — {@code is_active = false} is blocked when non-terminal orders exist.</li>
 *   <li>BR-OFF-07 — {@code code} is normalised to uppercase before persistence.</li>
 * </ul>
 *
 * <p>The {@code listForDropdown} method filters to active-only offices using the composite
 * index {@code idx_office_active(tp_account_id, is_active)} (DD-08).
 */
@Service
public class OfficeService {

    private static final Logger log = LoggerFactory.getLogger(OfficeService.class);

    /**
     * Standard 15-character Indian GSTIN regex.
     * Format: 2 digits (state code) + 5 uppercase letters (PAN prefix) +
     *         4 digits (PAN sequence) + 1 uppercase letter (PAN check) +
     *         1 alphanumeric (entity type) + 'Z' + 1 alphanumeric (check digit).
     */
    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    /** Terminal order statuses — an office with only these statuses can be deactivated. */
    private static final Set<OrderStatus> TERMINAL_STATUSES =
            Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    /** BOEM branch card "In transit" bucket — vehicle committed or moving. */
    private static final List<OrderStatus> TRANSIT_OR_FLEET =
            List.of(OrderStatus.IN_TRANSIT, OrderStatus.FLEET_CONFIRMED);

    private final OfficeRepository offices;
    private final OrderRepository orders;

    public OfficeService(OfficeRepository offices, OrderRepository orders) {
        this.offices = offices;
        this.orders = orders;
    }

    /**
     * Creates a new branch office for the given TP account.
     *
     * <p>Validates GSTIN format if provided, normalises {@code code} to uppercase,
     * and checks code uniqueness within the account before persisting.
     *
     * @param tpAccountId the TP account to create the office under
     * @param req         the create request (name, code, city, state required)
     * @return the persisted office as a response DTO
     * @throws ApiException {@code INVALID_GSTIN} if the GSTIN format is wrong
     * @throws ApiException {@code OFFICE_CODE_EXISTS} if the code is already taken for this account
     */
    @Transactional
    public OfficeResponseDto create(String tpAccountId, CreateOfficeRequest req) {
        if (req.gstin() != null && !GSTIN_PATTERN.matcher(req.gstin()).matches()) {
            throw new ApiException(ErrorCode.INVALID_GSTIN,
                    "GSTIN must be a valid 15-character Indian GSTIN");
        }

        String code = req.code().toUpperCase();

        if (offices.existsByCodeAndTpAccountId(code, tpAccountId)) {
            throw new ApiException(ErrorCode.OFFICE_CODE_EXISTS,
                    "Office code '" + code + "' already exists for this account");
        }

        assertNoDuplicateOfficeLocation(tpAccountId, req.name(), req.city(), req.state(), null);

        Office o = new Office();
        // SCHEMA: id is CHAR(36) UUID. Generate server-side because we don't use
        // @GeneratedValue (keeps the id strategy explicit for any reader).
        o.ensureId();
        o.setTpAccountId(tpAccountId);
        o.setName(req.name());
        o.setCode(code);
        o.setCity(req.city());
        o.setState(req.state());
        o.setPincode(blankToNull(req.pincode()));
        o.setAddress(req.address());
        o.setGstin(req.gstin());
        o.setActive(true);
        offices.save(o);
        log.info("Office persisted | id={} | tpAccountIdSuffix={} | code={}",
                o.getId(), suffix(tpAccountId), code);

        return toDto(o, OfficeOrderMetricsDto.empty());
    }

    /**
     * Returns all branch offices for the given TP account, including inactive ones.
     * Each office includes the total historical order count (all statuses).
     *
     * @param tpAccountId the TP account ID
     * @return list of all offices with order counts
     */
    @Transactional(readOnly = true)
    public List<OfficeResponseDto> list(String tpAccountId) {
        log.debug("OfficeRepository.findByTpAccountId | tpAccountIdSuffix={}", suffix(tpAccountId));
        List<Office> rows = offices.findByTpAccountId(tpAccountId);
        List<String> ids = rows.stream().map(Office::getId).toList();
        Map<String, OfficeOrderMetricsDto> metricsByOffice = loadOfficeOrderMetrics(tpAccountId, ids);
        return rows.stream()
                .map(o -> toDto(o, metricsByOffice.getOrDefault(o.getId(), OfficeOrderMetricsDto.empty())))
                .toList();
    }

    /**
     * Returns only active offices for use in order-assignment dropdowns (DD-08, BR-OFF-04).
     * Uses the composite index {@code idx_office_active} for efficient filtering.
     *
     * @param tpAccountId the TP account ID
     * @return active offices only, without order counts (dropdown use case)
     */
    @Transactional(readOnly = true)
    public List<OfficeResponseDto> listForDropdown(String tpAccountId) {
        return offices.findByTpAccountIdAndIsActive(tpAccountId, true).stream()
                .map(o -> toDto(o, OfficeOrderMetricsDto.empty()))
                .toList();
    }

    /**
     * Returns a single office by ID, scoped to the given TP account.
     *
     * @param tpAccountId the caller's TP account ID (for ownership check)
     * @param id          the office primary key
     * @return the office response DTO
     * @throws ApiException {@code OFFICE_NOT_FOUND} if the office doesn't exist or belongs to another account
     */
    @Transactional(readOnly = true)
    public OfficeResponseDto getById(String tpAccountId, String id) {
        Office o = findOwned(tpAccountId, id);
        Map<String, OfficeOrderMetricsDto> m = loadOfficeOrderMetrics(tpAccountId, List.of(id));
        return toDto(o, m.getOrDefault(id, OfficeOrderMetricsDto.empty()));
    }

    /**
     * Partially updates a branch office. Only non-null fields in the request are applied.
     *
     * <p>Enforces:
     * <ul>
     *   <li>BR-OFF-02: code uniqueness on change (excludes self).</li>
     *   <li>BR-OFF-03: GSTIN format validation.</li>
     *   <li>BR-OFF-06: blocks deactivation when active orders exist.</li>
     *   <li>BR-OFF-07: code normalised to uppercase.</li>
     * </ul>
     *
     * @param tpAccountId the caller's TP account ID (for ownership check)
     * @param id          the office primary key
     * @param req         the partial update request
     * @return the updated office response DTO
     * @throws ApiException {@code OFFICE_NOT_FOUND}          if the office is not found/accessible
     * @throws ApiException {@code INVALID_GSTIN}             if the new GSTIN format is invalid
     * @throws ApiException {@code OFFICE_CODE_EXISTS}        if the new code is already taken
     * @throws ApiException {@code OFFICE_HAS_ACTIVE_ORDERS}  if deactivation is blocked by active orders
     */
    @Transactional
    public OfficeResponseDto update(String tpAccountId, String id, UpdateOfficeRequest req) {
        Office o = findOwned(tpAccountId, id);

        if (req.gstin() != null && !GSTIN_PATTERN.matcher(req.gstin()).matches()) {
            throw new ApiException(ErrorCode.INVALID_GSTIN,
                    "GSTIN must be a valid 15-character Indian GSTIN");
        }

        if (req.code() != null) {
            String code = req.code().toUpperCase();
            if (offices.existsByCodeAndTpAccountIdAndIdNot(code, tpAccountId, id)) {
                throw new ApiException(ErrorCode.OFFICE_CODE_EXISTS,
                        "Office code '" + code + "' already exists for this account");
            }
            o.setCode(code);
        }

        // BR-OFF-06: block deactivation when non-terminal orders are assigned
        if (Boolean.FALSE.equals(req.isActive()) && o.isActive()) {
            if (orders.existsActiveOrdersByOfficeId(id, TERMINAL_STATUSES)) {
                throw new ApiException(ErrorCode.OFFICE_HAS_ACTIVE_ORDERS,
                        "Cannot deactivate office: active orders are still assigned to it");
            }
        }

        if (req.name() != null)     o.setName(req.name());
        if (req.city() != null)     o.setCity(req.city());
        if (req.state() != null)    o.setState(req.state());
        if (req.pincode() != null)  o.setPincode(blankToNull(req.pincode()));
        if (req.address() != null)  o.setAddress(req.address());
        if (req.gstin() != null)    o.setGstin(req.gstin());
        if (req.isActive() != null) o.setActive(req.isActive());

        assertNoDuplicateOfficeLocation(tpAccountId, o.getName(), o.getCity(), o.getState(), id);

        offices.save(o);
        log.info("Office updated | id={} | tpAccountIdSuffix={}", id, suffix(tpAccountId));
        Map<String, OfficeOrderMetricsDto> m = loadOfficeOrderMetrics(tpAccountId, List.of(id));
        return toDto(o, m.getOrDefault(id, OfficeOrderMetricsDto.empty()));
    }

    /**
     * Deletes an office if no orders have ever been assigned to it.
     * If orders exist (any status), the FK constraint will prevent deletion and this method
     * throws {@code OFFICE_HAS_ACTIVE_ORDERS} with guidance to deactivate instead (BR-OFF-04).
     *
     * @param tpAccountId the caller's TP account ID (for ownership check)
     * @param id          the office primary key
     * @throws ApiException {@code OFFICE_NOT_FOUND}         if the office is not found/accessible
     * @throws ApiException {@code OFFICE_HAS_ACTIVE_ORDERS} if any orders reference this office
     */
    @Transactional
    public void delete(String tpAccountId, String id) {
        findOwned(tpAccountId, id); // ownership check

        // Pre-check to return a clean error instead of letting the FK violation bubble up
        if (orders.countByAssignedOfficeId(id) > 0) {
            throw new ApiException(ErrorCode.OFFICE_HAS_ACTIVE_ORDERS,
                    "Cannot delete office with order history. Use PATCH is_active=false to deactivate instead.");
        }

        offices.deleteById(id);
        log.info("Office deleted | id={} | tpAccountIdSuffix={}", id, suffix(tpAccountId));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Loads an office by ID and asserts ownership by the given TP account.
     *
     * @param tpAccountId the expected owner
     * @param id          the office ID
     * @return the owned Office entity
     * @throws ApiException {@code OFFICE_NOT_FOUND} if not found or not owned
     */
    private Office findOwned(String tpAccountId, String id) {
        return offices.findByIdAndTpAccountId(id, tpAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND,
                        "Office not found: " + id));
    }

    /**
     * Maps an {@link Office} entity to an {@link OfficeResponseDto}, attaching the
     * pre-computed order count.
     *
     * @param o          the office entity
     * @param orderCount total orders assigned to this office
     * @return the response DTO
     */
    /**
     * Blocks a second office with the same name + city + state (trimmed, case-insensitive)
     * under one TP account — distinct from {@link ErrorCode#OFFICE_CODE_EXISTS}.
     */
    private void assertNoDuplicateOfficeLocation(String tpAccountId, String name, String city, String state,
                                                 String excludeOfficeId) {
        if (name == null || city == null || state == null) {
            return;
        }
        String n = name.trim();
        String c = city.trim();
        String st = state.trim();
        if (n.isEmpty() || c.isEmpty() || st.isEmpty()) {
            return;
        }
        boolean dup = excludeOfficeId == null
                ? offices.existsByTpAccountIdAndNameIgnoreCaseAndCityIgnoreCaseAndStateIgnoreCase(tpAccountId, n, c, st)
                : offices.existsByTpAccountIdAndNameIgnoreCaseAndCityIgnoreCaseAndStateIgnoreCaseAndIdNot(
                        tpAccountId, n, c, st, excludeOfficeId);
        if (dup) {
            throw new ApiException(ErrorCode.OFFICE_LOCATION_DUPLICATE,
                    "An office with this name, city, and state already exists for your account.");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    /**
     * Loads per-office order KPIs in one grouped query (empty {@code officeIds} → empty map).
     */
    private Map<String, OfficeOrderMetricsDto> loadOfficeOrderMetrics(String tpAccountId, List<String> officeIds) {
        if (officeIds == null || officeIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = orders.aggregateMetricsByOfficeIds(
                tpAccountId,
                officeIds,
                TRANSIT_OR_FLEET,
                OrderStatus.DELIVERED,
                DeliveryType.EXPRESS);
        Map<String, OfficeOrderMetricsDto> out = new HashMap<>();
        for (Object[] row : rows) {
            String officeId = (String) row[0];
            long total = toLong(row[1]);
            long transit = toLong(row[2]);
            long delivered = toLong(row[3]);
            long express = toLong(row[4]);
            double conv = total == 0 ? 0.0d : Math.round(1000.0 * delivered / total) / 10.0d;
            out.put(officeId, new OfficeOrderMetricsDto(total, transit, delivered, express, conv));
        }
        return out;
    }

    private static long toLong(Object n) {
        if (n == null) {
            return 0L;
        }
        return ((Number) n).longValue();
    }

    private OfficeResponseDto toDto(Office o, OfficeOrderMetricsDto metrics) {
        return new OfficeResponseDto(
                o.getId(),
                o.getName(),
                o.getCode(),
                o.getCity(),
                o.getState(),
                o.getPincode(),
                o.getAddress(),
                o.getGstin(),
                o.isActive(),
                metrics.totalOrders(),
                metrics,
                o.getCreatedAt()
        );
    }
}
