package com.cognilogistic.order.service;

import com.cognilogistic.order.dto.OrderDto;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.model.DeliveryType;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.order.model.VehicleType;
import com.cognilogistic.order.repository.CompanyRepository;
import com.cognilogistic.user.model.Office;
import com.cognilogistic.user.repository.OfficeRepository;
import org.springframework.stereotype.Component;

/**
 * Builds {@link OrderDto} instances from an {@link Order} entity.
 *
 * <p>Centralises the entity-to-wire mapping so both the TP-facing
 * {@link OrderService} and the customer-portal
 * {@link com.cognilogistic.order.service.portal.PortalOrderService} share one
 * implementation. The mapper handles:
 * <ul>
 *   <li>Resolving {@link Order#getAssignedOfficeId()} to an office display name
 *       via the {@link OfficeRepository}.</li>
 *   <li>Populating both members of every legacy/canonical alias pair on
 *       {@link OrderDto} (e.g. {@code priceInr} + {@code freightCostInr}) with
 *       identical values.</li>
 *   <li>Optional portal masking — when invoked via {@link #toPortalDto}, internal
 *       fields are nulled and the driver name is masked.</li>
 * </ul>
 *
 * <p><strong>Not yet implemented:</strong> {@code tenderNumber} resolution. That
 * field stays {@code null} in R1 and gets populated when PR R4 (tender broadcast
 * /award lifecycle) lands the {@code tender_order_refs} read path.
 *
 * <p><strong>N+1 caveat:</strong> the office name lookup hits the DB on every
 * {@link #toDto} call. For UAT scale this is fine; if list endpoints become slow
 * the call site should batch-load offices and pass them in.
 */
@Component
public class OrderDtoMapper {

    private final OfficeRepository officeRepository;
    private final CompanyRepository companyRepository;

    public OrderDtoMapper(OfficeRepository officeRepository, CompanyRepository companyRepository) {
        this.officeRepository = officeRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Builds the standard (TP-facing) DTO with all fields populated.
     *
     * @param o the order entity
     * @param c the linked customer (may be {@code null} — phone field then null)
     */
    public OrderDto toDto(Order o, Customer c) {
        return build(o, c, false);
    }

    /**
     * Builds the customer-portal DTO with PII masking applied:
     * {@code internalNotes}, {@code freightCostInr}, {@code priceInr}, and
     * {@code driverDl} are nulled; {@code driverName} is masked to first
     * name + last initial.
     */
    public OrderDto toPortalDto(Order o, Customer c) {
        return build(o, c, true);
    }

    private OrderDto build(Order o, Customer c, boolean portalMask) {
        // Office name resolution — null until the order is acknowledged. We scope by
        // the order's tp account just to be defensive; cross-tenant officeIds shouldn't
        // exist on an order, but if they do we want null rather than leaking a name.
        String officeName = null;
        if (o.getAssignedOfficeId() != null) {
            Office office = officeRepository.findById(o.getAssignedOfficeId()).orElse(null);
            if (office != null && office.getTpAccountId().equals(o.getTpAccountId())) {
                officeName = office.getName();
            }
        }

        String companyName = null;
        String companyId = o.getCompanyId();
        if (companyId != null && !companyId.isBlank()) {
            companyName = companyRepository.findById(companyId)
                    .filter(co -> co.getTpAccountId().equals(o.getTpAccountId()))
                    .map(co -> co.getName())
                    .orElse(null);
        }

        // Alias pairs — populate both members with identical values so the
        // front-end can switch to the canonical name without coordinating a cut-over.
        String goodsType = o.getGoodsType();
        VehicleType vehicleType = o.getVehicleType();
        String assignedVehicleStr = vehicleType != null ? vehicleType.getDisplayName() : null;
        String vehicleNumber = o.getVehicleRegistration();

        // Portal masking — internal-only fields disappear from the wire, driver name
        // is reduced to first+last-initial. Never leak driver_dl or freight cost.
        String driverName = portalMask ? maskDriverName(o.getDriverName()) : o.getDriverName();
        String driverDl = portalMask ? null : o.getDriverDl();
        String internalNotes = portalMask ? null : o.getInternalNotes();
        var freightCostInr = portalMask ? null : o.getPriceInr();

        return new OrderDto(
                o.getId(),
                o.getTpAccountId(),
                o.getCustomerId(),
                c == null ? null : c.getWhatsappPhone(),
                o.getCustomerName(),
                o.getCustomerGstin(),
                o.getCompanyId(),
                companyName,
                o.getOrderNo(),

                o.getAssignedOfficeId(),
                officeName,

                o.getStatus(),
                o.getOrderType(),
                o.getDeliveryType() != null ? o.getDeliveryType()
                        : (o.isExpress() ? DeliveryType.EXPRESS : DeliveryType.NORMAL),

                o.getPickupLocation(),
                o.getDropLocation(),
                o.getPickupDate(),
                o.getExpectedDeliveryDate(),

                goodsType,
                goodsType,                       // legacy alias materialDescription = goodsType

                o.getWeightKg(),
                o.getVolumeCbm(),

                o.getRequestedVehicleType(),
                o.getRequestedVehicleType(),     // legacy alias

                assignedVehicleStr,
                vehicleType,                     // legacy enum form

                vehicleNumber,
                vehicleNumber,                   // legacy alias vehicleRegistration

                o.getVehicleId(),                // @Transient — null in v5.0

                freightCostInr,                  // canonical
                freightCostInr,                  // legacy alias priceInr

                null,                            // tenderNumber — populated by R4 (tender module wiring)

                internalNotes,
                o.isExpress(),

                driverName,
                o.getDriverPhone(),
                driverDl,

                o.getVahanStatus(),
                o.getSarathiStatus(),

                o.getCancelledReason(),

                o.getCreatedAt(),
                o.getUpdatedAt());
    }

    /**
     * Masks a driver's full name to "First L." (first + last initial + period).
     * Single-word names pass through unchanged. Returns {@code null} when input is
     * blank so the wire field disappears entirely under portal masking.
     */
    private static String maskDriverName(String name) {
        if (name == null || name.isBlank()) return null;
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
