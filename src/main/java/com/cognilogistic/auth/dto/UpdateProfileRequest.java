package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/auth/profile} — completes the first-login
 * profile (onboarding step 2 → 3).
 *
 * <p>The caller is the authenticated user themselves; their {@code userId} comes
 * from the JWT, not the body. This endpoint:
 * <ul>
 *   <li>Sets {@code users.name = fullName}.</li>
 *   <li>Sets {@code users.whatsapp_number = whatsappNumber}.</li>
 *   <li>Sets {@code users.onboarding_step = 3}.</li>
 *   <li>For TP-side users, also sets {@code tp_accounts.name = orgName}
 *       (overwrites the placeholder {@code "Pending Setup"} written at signup).</li>
 * </ul>
 *
 * @param orgName         organisation name — required for TP-side roles, ignored for others
 * @param fullName        display name — required
 * @param whatsappNumber  WhatsApp number — required (10–15 digits, optional leading +)
 */
public record UpdateProfileRequest(

        @Size(max = 255, message = "orgName must be at most 255 characters")
        String orgName,

        @NotBlank
        @Size(min = 1, max = 255, message = "fullName must be 1–255 characters")
        String fullName,

        @NotBlank
        @Size(max = 15, message = "whatsappNumber must be at most 15 characters (DB column)")
        @Pattern(regexp = "\\+?\\d{10,15}", message = "whatsappNumber must be 10–15 digits, optional leading +")
        String whatsappNumber
) {}
