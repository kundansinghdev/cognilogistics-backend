package com.cognilogistic.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/orders/{id}/reassign}.
 *
 * <p>Reassignment is a privileged operation restricted to TP_ADMIN users (BR-05).
 * In V3.6, reassigning an office is an attribute update — it does not trigger a
 * status transition.
 *
 * @param newOfficeId the ID of the branch office to reassign the order to;
 *                    must belong to the same TP account as the order
 */
public record ReassignRequest(
        @NotBlank(message = "newOfficeId is required")
        @Size(max = 36, message = "newOfficeId must be at most 36 characters")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "newOfficeId must be a UUID")
        String newOfficeId
) {}
