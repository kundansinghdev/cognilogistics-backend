package com.cognilogistic.order.dto.portal;

import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.OrderType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/portal/orders} (and the
 * {@code /customer/orders} alias) — the customer creates an order
 * (BACKEND_GAPS §8).
 *
 * <p>R8 widens this DTO to match the TP-side {@code CreateOrderRequest} so the
 * FE can use the same payload on both sides. The legacy thin fields
 * ({@code pickup} / {@code drop} / {@code material} / {@code weightTons}) are
 * kept as fallbacks; the canonical names ({@code pickupLocation} /
 * {@code dropLocation} / {@code goodsType} / {@code weightKg}) take precedence
 * when both are supplied. Helper methods on the record do the coalescing.
 *
 * @param pickup                legacy alias for {@link #pickupLocation}
 * @param drop                  legacy alias for {@link #dropLocation}
 * @param material              legacy alias for {@link #goodsType}
 * @param pickupLocation        pickup address text (canonical)
 * @param dropLocation          delivery address text (canonical)
 * @param pickupDate            scheduled pickup date (defaults to today server-side when null)
 * @param expectedDeliveryDate  scheduled delivery date (optional)
 * @param goodsType             goods category (canonical)
 * @param orderType             FTL or PTL — required
 * @param deliveryType          NORMAL or EXPRESS (canonical)
 * @param weightKg              weight in kilograms (canonical)
 * @param weightTons            legacy alias — converted to kg server-side
 * @param volumeCbm             volume in cubic metres (optional)
 * @param requestedVehicle      customer-requested vehicle type, free-text
 * @param priceInr              optional price in INR (defaults to 0)
 * @param express               legacy boolean — when true sets deliveryType=EXPRESS
 */
public record PortalCreateOrderRequest(

        // Legacy thin fields
        String pickup,
        String drop,
        String material,

        // Canonical TP-style fields
        String pickupLocation,
        String dropLocation,
        LocalDate pickupDate,
        LocalDate expectedDeliveryDate,
        String goodsType,

        @NotNull OrderType orderType,
        DeliveryType deliveryType,

        BigDecimal weightKg,
        Double weightTons,
        BigDecimal volumeCbm,
        String requestedVehicle,

        // freight cost in INR — schema column is INT. See Order.priceInr Javadoc.
        Integer priceInr,
        boolean express
) {

    /** Canonical pickup location, falling back to {@link #pickup}. */
    public String effectivePickupLocation() {
        return notBlank(pickupLocation) ? pickupLocation : pickup;
    }

    /** Canonical drop location, falling back to {@link #drop}. */
    public String effectiveDropLocation() {
        return notBlank(dropLocation) ? dropLocation : drop;
    }

    /** Canonical goods type, falling back to {@link #material}. */
    public String effectiveGoodsType() {
        return notBlank(goodsType) ? goodsType : material;
    }

    /**
     * Canonical weight in kg. Returns {@link #weightKg} when set, else converts
     * {@link #weightTons} to kg, else {@code null}.
     */
    public BigDecimal effectiveWeightKg() {
        if (weightKg != null) return weightKg;
        if (weightTons != null) return BigDecimal.valueOf(Math.round(weightTons * 1000));
        return null;
    }

    /**
     * Resolved {@link DeliveryType}: explicit field wins; falls back to the
     * legacy boolean; defaults to NORMAL.
     */
    public DeliveryType effectiveDeliveryType() {
        if (deliveryType != null) return deliveryType;
        return express ? DeliveryType.EXPRESS : DeliveryType.NORMAL;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
