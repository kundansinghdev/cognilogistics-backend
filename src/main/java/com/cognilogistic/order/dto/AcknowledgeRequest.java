package com.cognilogistic.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Optional request body for {@code POST /api/v1/orders/{id}/acknowledge}.
 *
 * <p>V3.6: acknowledging an order is a combined step that both sets the branch office
 * and transitions the status to ACKNOWLEDGED in one call. There is no longer a separate
 * ASSIGNED intermediate state.
 *
 * <p><strong>Why {@code officeId} is not {@code @NotBlank}.</strong> The field is only
 * required when the order has no assigned office yet — otherwise it must be left null.
 * The service translates the missing-but-required case into a
 * {@code VALIDATION_ERROR} with {@code details.field=officeId} so the FE attaches the
 * message to the right input (mirrors the {@code SetupPinRequest} pattern in auth).
 *
 * @param officeId branch office to assign; required when the order has no
 *                 {@code assigned_office_id} yet; may be omitted (or null) when the office is already set
 */
public record AcknowledgeRequest(
        @Size(max = 36, message = "officeId must be at most 36 characters")
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$", message = "officeId must be a UUID")
        String officeId
) {}
