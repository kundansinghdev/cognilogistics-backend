package com.cognilogistic.order.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Catalogue of vehicle body types supported by the platform.
 *
 * <p>Each constant carries a human-readable {@code displayName} which is used as the
 * JSON serialised value via {@code @JsonValue}. This means the wire format is the
 * display string (e.g. {@code "14 ft"}) rather than the enum name ({@code "FOURTEEN_FT"}),
 * matching the values stored in the {@code fleet} table's {@code vehicle_type} column.
 */
public enum VehicleType {
    FOURTEEN_FT("14 ft"),
    TWENTY_TWO_FT("22 ft"),
    MULTI_AXLE("Multi-Axle"),
    TATA_ACE("Tata Ace"),
    CONTAINER("Container"),
    OPEN_BODY("Open Body"),
    TRAILER("Trailer"),
    MINI_TRUCK("Mini Truck");

    private final String displayName; // the value stored in DB and sent over the wire

    VehicleType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name used as the JSON serialised value for this vehicle type.
     *
     * @return the human-readable vehicle type label (e.g. "14 ft", "Tata Ace")
     */
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
