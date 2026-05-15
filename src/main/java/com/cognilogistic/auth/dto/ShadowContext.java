package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.UserRole;

/**
 * Pre-known identity context returned on {@link VerifyOtpResponse} when the
 * verified phone matches a {@code users.is_shadow=TRUE} row that was created
 * by another tenant before the user themself signed up (BACKEND_GAPS §1.10).
 *
 * <p>Two shadow-creation flows exist in the spec — though their write side is
 * out of scope for the current PR (BACKEND_GAPS §1.10 explicitly defers it):
 * <ul>
 *   <li><strong>Sender → partner network:</strong> a Sender / Shipper adds a
 *       phone to their broadcast allow-list. Creates a {@code PARTNER_TP}
 *       shadow row whose {@code orgName} comes from
 *       {@code partner_tp_profiles.company_name} and whose
 *       {@code sponsoringSenderName} comes (eventually) from
 *       {@code tp_partner_network} → {@code tp_accounts.name}.</li>
 *   <li><strong>Sender → company master / customer list:</strong> Sender adds a
 *       phone while creating a customer record. Creates a {@code CUSTOMER}
 *       shadow row whose {@code orgName} comes from
 *       {@code customers.legal_name} and whose
 *       {@code sponsoringSenderName} comes from
 *       {@code customers.created_by_tp_id} → {@code tp_accounts.name}.</li>
 * </ul>
 *
 * <p>When this object is populated, the front-end's signup wizard:
 * <ol>
 *   <li>Skips the role-pick step — {@link #role} wins; any client-supplied
 *       {@code roleHint} on {@code setup-pin} is ignored.</li>
 *   <li>Pre-fills the business-name field on {@code /confirm-name} with
 *       {@link #orgName}. User can confirm or edit.</li>
 *   <li>Shows a banner: <em>"You're already in {sponsoringSenderName}'s network.
 *       Your account is set up — just confirm your name and create a PIN."</em></li>
 * </ol>
 *
 * <p>{@code shadow} is {@code null} on {@link VerifyOtpResponse} for any phone
 * that doesn't match an active shadow row — the dominant case.
 *
 * @param role                  the role the shadow row was pre-created with
 *                              ({@code TP_ADMIN} / {@code PARTNER_TP} / {@code CUSTOMER})
 * @param orgName               business name from the relevant master record
 *                              (partner profile or customer); may be {@code null}
 *                              if the master record had none set
 * @param sponsoringSenderName  display name of the TP account that originally
 *                              added this phone. {@code null} until the
 *                              partner-network creation flow / lookup wires up
 *                              (CUSTOMER side resolves via
 *                              {@code customers.created_by_tp_id} today).
 */
public record ShadowContext(
        UserRole role,
        String orgName,
        String sponsoringSenderName) {
}
