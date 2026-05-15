package com.cognilogistic.order.controller.portal;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.dto.OrderStatusLogDto;
import com.cognilogistic.order.dto.portal.PortalCreateOrderRequest;
import com.cognilogistic.order.service.portal.PortalOrderService;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for customer portal order operations under {@code /api/v1/portal/orders}.
 *
 * <p>Accessible only to CUSTOMER-role JWT holders. Customers can view and create
 * their own orders, and view the status timeline. Sensitive TP-internal fields
 * (internal notes, driver DL, etc.) are masked or omitted from responses.
 *
 * <p>Logging mirrors the TP {@link com.cognilogistic.order.controller.OrderController}
 * pattern: {@code [ENTRY]} per call and {@link ControllerRequestLogging#withExitLog}
 * for {@code [EXIT]} lines (success or domain {@code ApiException}).
 */
@Tag(name = "Orders (Customer portal)", description = "CUSTOMER JWT. Duplicate paths: `/portal/orders` and `/customer/orders`.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping(path = {"/api/v1/portal/orders", "/api/v1/customer/orders"})
public class PortalOrderController {

    private static final Logger log = LoggerFactory.getLogger(PortalOrderController.class);

    private final PortalOrderService portalOrderService;

    public PortalOrderController(PortalOrderService portalOrderService) {
        this.portalOrderService = portalOrderService;
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    /**
     * Lists all orders belonging to the authenticated customer.
     *
     * @param me the authenticated customer principal
     * @return all orders for this customer with portal-safe field masking applied
     */
    @GetMapping
    public ApiResponse<List<OrderDto>> listOrders(@CurrentUser AuthPrincipal me) {
        log.info("[ENTRY] portalListOrders | userId={}", suffix(me != null ? me.userId() : null));
        return ControllerRequestLogging.withExitLog(PortalOrderController.class, "portalListOrders",
                () -> portalOrderService.listOrders(me));
    }

    /**
     * Retrieves a single order for the authenticated customer.
     *
     * @param me the authenticated customer principal
     * @param id the order ID (must belong to this customer)
     * @return the order DTO with portal-safe masking
     */
    @GetMapping("/{id}")
    public ApiResponse<OrderDto> getOrder(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] portalGetOrder | id={} | userId={}", suffix(id), suffix(me != null ? me.userId() : null));
        return ControllerRequestLogging.withExitLog(PortalOrderController.class, "portalGetOrder",
                () -> portalOrderService.getOrder(me, id));
    }

    /**
     * Returns the status timeline (audit log) for the specified order.
     *
     * @param me the authenticated customer principal
     * @param id the order ID (must belong to this customer)
     * @return list of status log entries ordered by timestamp ascending
     */
    @GetMapping("/{id}/timeline")
    public ApiResponse<List<OrderStatusLogDto>> getTimeline(@CurrentUser AuthPrincipal me, @PathVariable String id) {
        log.info("[ENTRY] portalGetTimeline | id={} | userId={}", suffix(id), suffix(me != null ? me.userId() : null));
        return ControllerRequestLogging.withExitLog(PortalOrderController.class, "portalGetTimeline",
                () -> portalOrderService.getTimeline(me, id));
    }

    /**
     * Creates a new order on behalf of the authenticated customer.
     * The order is associated with the TP account that originally created the customer record.
     *
     * @param me  the authenticated customer principal
     * @param req order creation parameters (pickup, drop, material, type, weight)
     * @return the newly created order DTO
     */
    @PostMapping
    public ApiResponse<OrderDto> createOrder(@CurrentUser AuthPrincipal me,
                                             @Valid @RequestBody PortalCreateOrderRequest req) {
        log.info("[ENTRY] portalCreateOrder | userId={} | orderType={}",
                suffix(me != null ? me.userId() : null), req.orderType());
        return ControllerRequestLogging.withExitLog(PortalOrderController.class, "portalCreateOrder",
                () -> portalOrderService.createOrder(me, req));
    }
}
