package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.DeliveryType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PATCH /api/v1/orders/{id}} — partial order update.
 *
 * <p>Only non-null fields are applied to the existing order (BR-08: editing is
 * only permitted before {@code FLEET_CONFIRMED}). All fields are optional.
 *
 * <p>Aligned with the front-end's update payload (BACKEND_GAPS §2.3) which
 * carries {@code office} (by name), {@code assignedVehicle}, {@code vehicleNumber},
 * {@code freightCostInr}. The service resolves {@code office} (display name)
 * to {@code assignedOfficeId} server-side via the offices table.
 *
 * @param customerWhatsappPhone  when non-blank, replaces the linked customer (normalised; creates or reuses shadow)
 * @param goodsType              goods category (canonical)
 * @param materialDescription    legacy alias for {@link #goodsType}
 * @param weightKg               weight in kilograms
 * @param volumeCbm              volume in cubic metres
 * @param requestedVehicle       customer-requested vehicle type
 * @param freightCostInr         freight cost in INR (canonical)
 * @param priceInr               legacy alias for {@link #freightCostInr}
 * @param internalNotes          TP-internal notes
 * @param deliveryType           NORMAL / EXPRESS (canonical)
 * @param express                legacy boolean alias for {@code deliveryType == EXPRESS}
 * @param assignedOfficeId       branch office id (canonical)
 * @param office                 branch office display name; resolved to id server-side
 * @param vehicleNumber          assigned vehicle registration (canonical, alias of legacy {@code vehicleRegistration})
 * @param assignedVehicle        assigned vehicle body-type string (matches a {@link com.cognilogistic.order.model.VehicleType} display name)
 * @param pickupLocation         pickup address text
 * @param dropLocation           delivery address text
 * @param pickupDate             scheduled pickup date
 * @param expectedDeliveryDate   scheduled delivery date
 */
public record UpdateOrderRequest(

        // Empty string is treated as "no change" by OrderService.update (mirrors
        // CreateOrderRequest's contract). A non-empty value must be 10–15 digits with
        // an optional leading `+`. Null is also accepted and means "leave existing".
        @Size(max = 20, message = "customerWhatsappPhone must be at most 20 characters")
        @Pattern(regexp = "^$|^\\+?\\d{10,15}$", message = "phone must be 10-15 digits")
        String customerWhatsappPhone,

        @Size(max = 100, message = "goodsType must be at most 100 characters")
        String goodsType,
        @Size(max = 100, message = "materialDescription must be at most 100 characters")
        String materialDescription,

        @DecimalMin(value = "0.0001", inclusive = true, message = "weightKg must be positive")
        @DecimalMax(value = "9999999.99", inclusive = true, message = "weightKg is too large")
        BigDecimal weightKg,
        @DecimalMin(value = "0.0001", inclusive = true, message = "volumeCbm must be positive")
        @DecimalMax(value = "99999.999", inclusive = true, message = "volumeCbm is too large")
        BigDecimal volumeCbm,
        @Size(max = 50, message = "requestedVehicle must be at most 50 characters")
        String requestedVehicle,

        // freight cost in INR — schema column is INT. See Order.priceInr Javadoc.
        @Min(value = 0, message = "freightCostInr cannot be negative")
        @Max(value = 999_999_999, message = "freightCostInr is too large")
        Integer freightCostInr,
        @Min(value = 0, message = "priceInr cannot be negative")
        @Max(value = 999_999_999, message = "priceInr is too large")
        Integer priceInr,

        @Size(max = 1000, message = "internalNotes must be at most 1000 characters")
        String internalNotes,

        DeliveryType deliveryType,
        Boolean express,

        @Size(max = 36, message = "assignedOfficeId must be at most 36 characters")
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "assignedOfficeId must be a UUID")
        String assignedOfficeId,
        @Size(max = 255, message = "office must be at most 255 characters")
        String office,

        // Indian RTO format (e.g. KA01AB1234). Case-insensitive — service uppercases before
        // persisting so DB-level lookups stay deterministic.
        @Size(max = 20, message = "vehicleNumber must be at most 20 characters")
        @Pattern(regexp = "^$|^[A-Za-z]{2}[0-9]{1,2}[A-Za-z]{1,3}[0-9]{1,4}$",
                message = "Invalid Indian vehicle registration")
        String vehicleNumber,
        @Size(max = 50, message = "assignedVehicle must be at most 50 characters")
        String assignedVehicle,

        @Size(max = 500, message = "pickupLocation must be at most 500 characters")
        String pickupLocation,
        @Size(max = 500, message = "dropLocation must be at most 500 characters")
        String dropLocation,
        @FutureOrPresent(message = "pickupDate cannot be in the past")
        LocalDate pickupDate,
        @FutureOrPresent(message = "expectedDeliveryDate cannot be in the past")
        LocalDate expectedDeliveryDate
) {

    /** Canonical goods type, falling back to legacy {@code materialDescription}. */
    public String effectiveGoodsType() {
        return goodsType != null ? goodsType : materialDescription;
    }

    /** Canonical freight cost, falling back to legacy {@code priceInr}. */
    public Integer effectiveFreightCostInr() {
        return freightCostInr != null ? freightCostInr : priceInr;
    }

    /**
     * Resolves the explicit delivery flag. Returns {@code null} when neither field
     * is supplied (so the caller leaves the existing value alone — null = no-op).
     */
    public Boolean effectiveExpress() {
        if (deliveryType != null) return deliveryType == DeliveryType.EXPRESS;
        return express;
    }
}
