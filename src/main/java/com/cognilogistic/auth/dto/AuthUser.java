package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.UserRole;

/**
 * Identity payload returned to the client on login / profile responses.
 *
 * <p>Mirrors the front-end's {@code AuthUser} TypeScript type
 * (BACKEND_GAPS §1.4) so the FE doesn't have to derive {@code profileComplete} or
 * {@code plan} from multiple separate fields. The wire shape:
 *
 * <pre>{@code
 * {
 *   "id": "uuid",
 *   "phone": "+919876543210",
 *   "role": "TP_ADMIN",
 *   "name": "Vikram Singh",
 *   "whatsappNumber": "9876543210",
 *   "email": null,
 *   "onboardingStep": 3,
 *   "profileComplete": true,
 *   "tpAccountId": "uuid",
 *   "orgName": "Bhoomihaar Express",
 *   "plan": "PREMIUM",
 *   "accountStatus": "APPROVED",
 *   "partnerId": null,
 *   "customerId": null,
 *   "isImpersonated": false,
 *   "impersonatedByUserId": null
 * }
 * }</pre>
 *
 * <p><strong>Why {@code plan} and {@code accountStatus} are strings, not enums:</strong>
 * the canonical enum types live in the {@code user} module ({@code Plan},
 * {@code AccountStatus}). Keeping the auth-module DTO free of those imports
 * means every call site that builds an {@code AuthUser} only needs the auth
 * module, and the JSON payload still carries identical strings.
 *
 * @param id                    user UUID
 * @param phone                 phone in E.164
 * @param role                  one of the five {@link UserRole} values
 * @param name                  display name; {@code null} until onboarding step 2
 * @param whatsappNumber        WhatsApp contact; {@code null} until onboarding step 2
 * @param email                 optional metadata only, never used as login key
 * @param onboardingStep        1, 2, or 3 — see User entity for semantics
 * @param profileComplete       {@code true} when {@link #onboardingStep} == 3
 * @param tpAccountId           TP tenancy id; {@code null} for PARTNER_TP, CUSTOMER, COGNILOGISTIC_ADMIN
 * @param orgName               organisation name from {@code tp_accounts.name}; null when {@link #tpAccountId} is null
 * @param plan                  TP plan as the {@code Plan} enum's name() string ({@code "BASIC"} / {@code "PREMIUM"} / {@code "ENTERPRISE"}); null without a TP context
 * @param accountStatus         TP signup state as the {@code AccountStatus} enum's name() string ({@code "PENDING"} / {@code "APPROVED"} / {@code "REJECTED"}); null without a TP context
 * @param partnerId             FK {@code partner_tp_profiles.id}; populated only for PARTNER_TP role
 * @param customerId            FK {@code customers.id}; populated only for CUSTOMER role
 * @param isImpersonated        {@code true} when the session is an admin impersonation (see API_REFERENCE §1.2)
 * @param impersonatedByUserId  the COGNILOGISTIC_ADMIN's user id when {@link #isImpersonated}; null otherwise
 */
public record AuthUser(
        String id,
        String phone,
        UserRole role,
        String name,
        String whatsappNumber,
        String email,
        Integer onboardingStep,
        boolean profileComplete,
        String tpAccountId,
        String orgName,
        String plan,
        String accountStatus,
        String partnerId,
        String customerId,
        boolean isImpersonated,
        String impersonatedByUserId) {
}
