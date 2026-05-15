package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.OrderStatus;
import com.cognilogistic.order.model.OrderType;
import com.cognilogistic.order.model.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-model DTO representing an order in API responses.
 *
 * <p>Returned by both the TP-facing order endpoints and the customer portal.
 * Portal responses null out internal-only fields ({@code internalNotes},
 * {@code freightCostInr}, {@code driverDl}) and mask {@code driverName} to
 * first name + last initial.
 *
 * <p><strong>Wire compatibility (rolling cutover).</strong> Several fields appear
 * twice on the wire under both their legacy backend name and their canonical
 * front-end name. The two members in each pair always carry identical values.
 * Front-end clients should switch to the canonical name; the legacy alias will
 * be dropped in a follow-up PR. Pairs:
 * <ul>
 *   <li>{@code priceInr} (legacy) ↔ {@code freightCostInr} (canonical)</li>
 *   <li>{@code vehicleRegistration} (legacy) ↔ {@code vehicleNumber} (canonical)</li>
 *   <li>{@code vehicleType} (legacy enum) ↔ {@code assignedVehicle} (canonical string)</li>
 *   <li>{@code requestedVehicleType} ↔ {@code requestedVehicle}</li>
 *   <li>{@code materialDescription} (legacy, transient) ↔ {@code goodsType} (canonical)</li>
 * </ul>
 *
 * <p><strong>Schema reference:</strong> {@code orders} + {@code order_details}
 * (schema.sql v5.0 lines 388–449).
 *
 * @param id                       database primary key (CHAR(36) UUID)
 * @param tpAccountId              owning Transport Provider tenancy
 * @param customerId               FK to the customer record
 * @param customerWhatsappPhone    denormalised customer phone for display
 * @param customerName             denormalised customer name (snapshot at order time)
 * @param customerGstin            denormalised customer GSTIN (snapshot)
 * @param companyId                optional FK to Company Master
 * @param companyName              resolved legal name when {@code companyId} is set
 * @param orderNo                  human-readable order number, e.g. {@code COG-20260508-0001}
 * @param assignedOfficeId         branch office id; null until ACKNOWLEDGED
 * @param office                   branch office display name (server-resolved); null until ACKNOWLEDGED
 * @param status                   current lifecycle status
 * @param orderType                FTL or PTL
 * @param deliveryType             NORMAL or EXPRESS — kept in sync with {@link #express}
 * @param pickupLocation           pickup address text
 * @param dropLocation             delivery address text
 * @param pickupDate               scheduled pickup date
 * @param expectedDeliveryDate     scheduled delivery date (optional)
 * @param goodsType                goods category (canonical)
 * @param materialDescription      legacy alias for {@link #goodsType}; identical value
 * @param weightKg                 weight in kilograms
 * @param volumeCbm                volume in cubic metres
 * @param requestedVehicle         customer-requested vehicle type (canonical name)
 * @param requestedVehicleType     legacy alias for {@link #requestedVehicle}
 * @param assignedVehicle          assigned vehicle body-type string (matches {@link VehicleType} display name)
 * @param vehicleType              legacy enum form of {@link #assignedVehicle}
 * @param vehicleNumber            assigned vehicle registration (uppercase Indian format)
 * @param vehicleRegistration      legacy alias for {@link #vehicleNumber}
 * @param vehicleId                optional FK to fleet vehicle (post-UAT)
 * @param freightCostInr           freight cost in INR (whole rupees) — canonical
 * @param priceInr                 legacy alias for {@link #freightCostInr}
 * @param tenderNumber             back-reference tender number when bundled into a tender
 * @param internalNotes            TP-internal notes; null in portal responses
 * @param express                  legacy boolean alias for {@code deliveryType == EXPRESS}
 * @param driverName               driver name (masked in portal responses)
 * @param driverPhone              driver contact phone
 * @param driverDl                 driver licence number; null in portal responses
 * @param vahanStatus              outcome of Vahan verification (advisory)
 * @param sarathiStatus            outcome of Sarathi verification (advisory)
 * @param cancelledReason          reason text when status is CANCELLED (transient)
 * @param createdAt                JPA audit timestamp
 * @param updatedAt                JPA audit timestamp
 */
public record OrderDto(

        String id,
        String tpAccountId,
        String customerId,
        String customerWhatsappPhone,
        String customerName,
        String customerGstin,
        String companyId,
        String companyName,
        String orderNo,

        String assignedOfficeId,
        String office,

        OrderStatus status,
        OrderType orderType,
        DeliveryType deliveryType,

        String pickupLocation,
        String dropLocation,
        LocalDate pickupDate,
        LocalDate expectedDeliveryDate,

        String goodsType,
        String materialDescription,

        BigDecimal weightKg,
        BigDecimal volumeCbm,

        String requestedVehicle,
        String requestedVehicleType,

        String assignedVehicle,
        VehicleType vehicleType,

        String vehicleNumber,
        String vehicleRegistration,
        String vehicleId,

        // freight cost in INR — schema column is INT (whole rupees), so the wire
        // type matches: an int-shaped JSON number. Aligned with Order.priceInr type.
        Integer freightCostInr,
        Integer priceInr,

        String tenderNumber,

        String internalNotes,
        boolean express,

        String driverName,
        String driverPhone,
        String driverDl,

        String vahanStatus,
        String sarathiStatus,

        String cancelledReason,

        Instant createdAt,
        Instant updatedAt
) {}
