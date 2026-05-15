package com.cognilogistic.order.dto;

import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.OrderType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/orders} — create a new order.
 *
 * <p>Canonical fields align with the TP create-order UI. Legacy aliases ({@code materialDescription},
 * {@code weightTons}, {@code priceInr}, {@code express}) remain supported via {@code effective*} helpers.
 *
 * <p><strong>Customer identification (BR-04).</strong> WhatsApp phone is optional at create time.
 * When provided, it is normalised and used to find or create a shadow customer. When omitted and
 * {@code companyId} resolves to a company with {@code primary_contact_phone}, that number is used.
 * When omitted and there is no company contact phone, a new shadow customer is created with
 * {@code phone = NULL} (multiple such rows are allowed until a phone is captured later).
 *
 * @param customerWhatsappPhone optional; when set must be 10–15 digits (optional leading {@code +})
 * @param companyId             optional Company Master id (must belong to caller's TP when set)
 */
public record CreateOrderRequest(

        // Empty string is allowed (optional phone). When non-empty, pattern enforces 10–15 digits;
        // {@link com.cognilogistic.order.service.OrderService#create} normalises, may copy company
        // contact phone, and may create a phone-less shadow customer when neither is available.
        @Size(max = 20, message = "customerWhatsappPhone must be at most 20 characters")
        @Pattern(regexp = "^$|^\\+?\\d{10,15}$", message = "phone must be 10-15 digits")
        String customerWhatsappPhone,

        @NotBlank(message = "customerName is required")
        @Size(max = 255, message = "customerName must be at most 255 characters")
        String customerName,

        @Size(max = 15, message = "customerGstin must be exactly 15 characters")
        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "customerGstin must be a valid 15-character Indian GSTIN")
        String customerGstin,

        @Size(max = 36, message = "companyId must be at most 36 characters")
        // UUID format check (CHAR(36)). Empty allowed so existing callers without a company link
        // continue to work; service-level lookup determines whether the id is valid.
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "companyId must be a UUID")
        String companyId,

        @NotBlank(message = "pickupLocation is required")
        @Size(max = 500, message = "pickupLocation must be at most 500 characters")
        String pickupLocation,

        @NotBlank(message = "dropLocation is required")
        @Size(max = 500, message = "dropLocation must be at most 500 characters")
        String dropLocation,

        @NotNull(message = "pickupDate is required")
        @FutureOrPresent(message = "pickupDate cannot be in the past")
        LocalDate pickupDate,

        @FutureOrPresent(message = "expectedDeliveryDate cannot be in the past")
        LocalDate expectedDeliveryDate,

        // goodsType is canonical; materialDescription is the legacy alias. The service raises
        // VALIDATION_ERROR with details.fields.goodsType when neither is supplied so the FE can
        // attach the message to the right field — bean-validation would attach it to whichever
        // happens to be evaluated first.
        @Size(max = 100, message = "goodsType must be at most 100 characters")
        String goodsType,
        @Size(max = 100, message = "materialDescription must be at most 100 characters")
        String materialDescription,

        @NotNull(message = "orderType is required") OrderType orderType,
        DeliveryType deliveryType,
        Boolean express,

        @DecimalMin(value = "0.0001", inclusive = true, message = "weightKg must be positive")
        @DecimalMax(value = "9999999.99", inclusive = true, message = "weightKg is too large")
        BigDecimal weightKg,

        @DecimalMin(value = "0.000001", inclusive = true, message = "weightTons must be positive")
        @DecimalMax(value = "9999.999", inclusive = true, message = "weightTons is too large")
        Double weightTons,

        @DecimalMin(value = "0.0001", inclusive = true, message = "volumeCbm must be positive")
        @DecimalMax(value = "99999.999", inclusive = true, message = "volumeCbm is too large")
        BigDecimal volumeCbm,

        @Size(max = 50, message = "requestedVehicle must be at most 50 characters")
        String requestedVehicle,

        @Min(value = 0, message = "freightCostInr cannot be negative")
        @Max(value = 999_999_999, message = "freightCostInr is too large")
        Integer freightCostInr,

        @Min(value = 0, message = "priceInr cannot be negative")
        @Max(value = 999_999_999, message = "priceInr is too large")
        Integer priceInr,

        @Size(max = 1000, message = "internalNotes must be at most 1000 characters")
        String internalNotes
) {

    /** Returns the canonical goods type, falling back to legacy {@code materialDescription}. */
    public String effectiveGoodsType() {
        return goodsType != null ? goodsType : materialDescription;
    }

    /** Returns the canonical freight cost, falling back to legacy {@code priceInr}. */
    public Integer effectiveFreightCostInr() {
        return freightCostInr != null ? freightCostInr : priceInr;
    }

    /**
     * Returns the canonical weight in kg, converting from legacy {@code weightTons} if
     * the canonical field isn't set. Returns {@code null} only when neither is supplied.
     */
    public BigDecimal effectiveWeightKg() {
        if (weightKg != null) return weightKg;
        if (weightTons != null) return BigDecimal.valueOf(Math.round(weightTons * 1000));
        return null;
    }

    /**
     * Resolves the canonical {@link DeliveryType}. Order of precedence:
     * <ol>
     *   <li>Explicit {@link #deliveryType}.</li>
     *   <li>Legacy {@link #express} = true → EXPRESS.</li>
     *   <li>Default NORMAL.</li>
     * </ol>
     */
    public DeliveryType effectiveDeliveryType() {
        if (deliveryType != null) return deliveryType;
        if (express != null && express) return DeliveryType.EXPRESS;
        return DeliveryType.NORMAL;
    }

    /** True when the resolved delivery type is EXPRESS. */
    public boolean effectiveExpress() {
        return effectiveDeliveryType() == DeliveryType.EXPRESS;
    }
}
