package com.cognilogistic.order.service;

import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.repository.TpAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for generating HTML shipping documents:
 * <ul>
 *   <li><b>GR (Goods Receipt)</b> — per-order document issued once an order is ACKNOWLEDGED or later;
 *       confirms receipt of goods from the shipper</li>
 *   <li><b>LR (Lorry Receipt)</b> — per Connected Lot document grouping all orders on the same
 *       vehicle on the same day; serves as the transport manifest</li>
 * </ul>
 *
 * <p>Documents are generated as HTML strings and returned with {@code Content-Type: text/html}
 * so the browser can render or print them directly.
 */
@Service
public class GrLrDocumentService {

    private static final Logger log = LoggerFactory.getLogger(GrLrDocumentService.class);

    // GR is only meaningful once the order is past CREATED; CANCELLED orders do not get GRs
    private static final Set<OrderStatus> GR_ELIGIBLE = EnumSet.of(
            OrderStatus.ACKNOWLEDGED, OrderStatus.FLEET_CONFIRMED,
            OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final TpAccountRepository tpAccounts;

    public GrLrDocumentService(OrderRepository orders,
                               CustomerRepository customers,
                               TpAccountRepository tpAccounts) {
        this.orders = orders;
        this.customers = customers;
        this.tpAccounts = tpAccounts;
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    /**
     * Generates an HTML Goods Receipt document for the specified order.
     * The order must be in {@code ACKNOWLEDGED} status or later.
     *
     * @param tpAccountId the TP account ID (used for scope check — prevents cross-account access)
     * @param orderId     the order to generate the GR for
     * @return the GR as an HTML string ready for browser rendering
     */
    @Transactional(readOnly = true)
    public String generateGrHtml(String tpAccountId, String orderId) {
        Order o = orders.findById(orderId).orElse(null);
        if (o == null) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        if (!o.getTpAccountId().equals(tpAccountId)) {
            log.warn("GR cross-tenant blocked | orderId={} | callerTp={} | ownerTp={}",
                    suffix(orderId), suffix(tpAccountId), suffix(o.getTpAccountId()));
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }

        if (!GR_ELIGIBLE.contains(o.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "GR is only available once order is ACKNOWLEDGED or later");
        }

        Customer customer = customers.findById(o.getCustomerId()).orElse(null);
        String customerPhone = "-";
        if (customer != null && customer.getWhatsappPhone() != null && !customer.getWhatsappPhone().isBlank()) {
            customerPhone = customer.getWhatsappPhone();
        }

        String orgName = resolveOrgName(tpAccountId);
        String html = buildGrHtml(o, customerPhone, orgName);
        log.info("GR document generated | orderId={} | orderNo={} | tp={} | status={}",
                suffix(orderId), o.getOrderNo(), suffix(tpAccountId), o.getStatus());
        return html;
    }

    /**
     * Generates an HTML Lorry Receipt document for a Connected Lot.
     * Only orders in the lot that belong to the specified TP account and vehicle number are included.
     *
     * @param tpAccountId   the TP account ID (scope check)
     * @param vehicleNumber the vehicle registration shared by all orders in the lot
     * @param pickupDate    the pickup date string shown on the document header
     * @param orderIds      the IDs of orders to include in the LR
     * @return the LR as an HTML string, or throws if no matching orders found
     */
    @Transactional(readOnly = true)
    public String generateLrHtml(String tpAccountId, String vehicleNumber, String pickupDate,
                                  List<String> orderIds) {
        List<Order> lot = orders.findAllById(orderIds).stream()
                .filter(o -> o.getTpAccountId().equals(tpAccountId))
                .filter(o -> vehicleNumber.equals(o.getVehicleRegistration()))
                .toList();

        if (lot.isEmpty()) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "No orders found for this connected lot");
        }

        String orgName = resolveOrgName(tpAccountId);
        String html = buildLrHtml(lot, vehicleNumber, pickupDate, orgName);
        log.info("LR document generated | tp={} | consignments={} | pickupDate={}",
                suffix(tpAccountId), lot.size(), pickupDate);
        return html;
    }

    private String resolveOrgName(String tpAccountId) {
        return tpAccounts.findSummary(tpAccountId)
                .map(TpAccountRepository.TpAccountSummary::name)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Transport Provider");
    }

    private String buildGrHtml(Order o, String customerPhone, String orgName) {
        String weight = formatWeightMt(o.getWeightKg());
        // priceInr is Integer (whole rupees, schema column INT) — Integer's
        // toString gives a clean rendering for GR/LR (no decimal point).
        String price = o.getPriceInr() != null
                ? "₹" + o.getPriceInr()
                : "-";
        String date = DATE_FMT.format(o.getCreatedAt());

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>GR — Order #%d</title>
                <style>
                  body{font-family:Arial,sans-serif;font-size:12px;margin:24px}
                  h1{font-size:18px;margin-bottom:4px}
                  .subtitle{font-size:10px;color:#555;margin-bottom:16px}
                  table{width:100%%;border-collapse:collapse}
                  td,th{border:1px solid #ccc;padding:6px 8px;vertical-align:top}
                  th{background:#f0f0f0;font-weight:600;width:35%%}
                </style>
                </head>
                <body>
                <h1>Goods Receipt (GR)</h1>
                <div class="subtitle">%s — Order Management Platform</div>
                <table>
                  <tr><th>Order No</th><td>#%d</td></tr>
                  <tr><th>Date</th><td>%s</td></tr>
                  <tr><th>Customer Phone</th><td>%s</td></tr>
                  <tr><th>Material</th><td>%s</td></tr>
                  <tr><th>Weight (MT)</th><td>%s</td></tr>
                  <tr><th>Order Type</th><td>%s</td></tr>
                  <tr><th>Vehicle Reg.</th><td>%s</td></tr>
                  <tr><th>Driver</th><td>%s</td></tr>
                  <tr><th>Price</th><td>%s</td></tr>
                  <tr><th>Status</th><td>%s</td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(orgName), o.getId(), o.getId(), date, customerPhone,
                nvl(o.getMaterialDescription()), weight,
                o.getOrderType(), nvl(o.getVehicleRegistration()),
                nvl(o.getDriverName()), price, o.getStatus());
    }

    private String buildLrHtml(List<Order> lot, String vehicleNumber, String pickupDate, String orgName) {
        StringBuilder rows = new StringBuilder();
        for (Order o : lot) {
            rows.append("<tr><td>#%d</td><td>%s</td><td>%s</td><td>%s</td></tr>".formatted(
                    o.getId(),
                    nvl(o.getMaterialDescription()),
                    formatWeightMt(o.getWeightKg()),
                    o.getStatus()));
        }

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>LR — Vehicle %s</title>
                <style>
                  body{font-family:Arial,sans-serif;font-size:12px;margin:24px}
                  h1{font-size:18px;margin-bottom:4px}
                  .subtitle{font-size:10px;color:#555;margin-bottom:16px}
                  table{width:100%%;border-collapse:collapse}
                  td,th{border:1px solid #ccc;padding:6px 8px}
                  th{background:#f0f0f0;font-weight:600}
                </style>
                </head>
                <body>
                <h1>Lorry Receipt (LR)</h1>
                <div class="subtitle">%s — Connected Lot</div>
                <p><strong>Vehicle:</strong> %s &nbsp;&nbsp; <strong>Pickup Date:</strong> %s &nbsp;&nbsp;
                   <strong>Total Consignments:</strong> %d</p>
                <table>
                  <thead><tr><th>GR / Order</th><th>Material</th><th>Weight (MT)</th><th>Status</th></tr></thead>
                  <tbody>%s</tbody>
                </table>
                </body>
                </html>
                """.formatted(vehicleNumber, escapeHtml(orgName), vehicleNumber, pickupDate, lot.size(), rows);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String nvl(String s) {
        return s != null ? s : "-";
    }

    /**
     * Renders stored {@code weight_kg} as metric tons for GR/LR — matches TP create form
     * ({@code weightTons} × 1000 persisted server-side).
     */
    private static String formatWeightMt(BigDecimal weightKg) {
        if (weightKg == null) {
            return "-";
        }
        BigDecimal tons = weightKg.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (tons.signum() <= 0) {
            return "-";
        }
        return tons.toPlainString() + " MT";
    }
}
