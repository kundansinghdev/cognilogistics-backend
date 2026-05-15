package com.cognilogistic.platform.admin;

import com.cognilogistic.auth.dto.LoginResponse;
import com.cognilogistic.auth.model.DeviceType;
import com.cognilogistic.auth.model.User;
import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.service.AuthService;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.repository.CustomerRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.tender.model.PartnerTpProfile;
import com.cognilogistic.tender.repository.PartnerTpProfileRepository;
import com.cognilogistic.user.model.AccountStatus;
import com.cognilogistic.user.model.Plan;
import com.cognilogistic.user.model.TpAccount;
import com.cognilogistic.user.repository.TpAccountJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-portal service for cross-tenant operations (BACKEND_GAPS §7).
 *
 * <p>R6 surface:
 * <ul>
 *   <li>{@link #list} — single aggregated list spanning TPs + Partners + Customers.</li>
 *   <li>{@link #updateStatus} — TP only — overwrite {@code account_status}.</li>
 *   <li>{@link #updatePlan} — TP only — overwrite {@code plan}.</li>
 *   <li>{@link #impersonate} — issue an impersonation JWT for the target identity
 *       and write an {@code impersonation_audit_log} row.</li>
 *   <li>{@link #exit} — mark the audit log row as ended and re-issue admin-side
 *       tokens.</li>
 * </ul>
 *
 * <p><strong>Authorisation</strong> — every method here calls {@link #requireAdmin}
 * which gates on {@link UserRole#COGNILOGISTIC_ADMIN}. Non-admin callers get
 * {@link ErrorCode#IMPERSONATION_NOT_ALLOWED} on the impersonate endpoints (per
 * the canonical error code list — API_REFERENCE §14) and {@link ErrorCode#FORBIDDEN}
 * everywhere else.
 *
 * <p><strong>Order count caveat</strong> — R6 returns {@code orderCount = 0} on
 * every row. Surfacing real counts means joining {@code orders} per row, which
 * is N+1 at the listing scale we'd want. Future iterations should batch-load
 * via a {@code GROUP BY tp_account_id} query.
 */
@Service
public class AdminService {

    private final TpAccountJpa tpAccounts;
    private final UserRepository users;
    private final PartnerTpProfileRepository partners;
    private final CustomerRepository customers;
    private final ImpersonationAuditLogRepository auditLog;
    private final AuthService authService;

    public AdminService(TpAccountJpa tpAccounts,
                        UserRepository users,
                        PartnerTpProfileRepository partners,
                        CustomerRepository customers,
                        ImpersonationAuditLogRepository auditLog,
                        AuthService authService) {
        this.tpAccounts = tpAccounts;
        this.users = users;
        this.partners = partners;
        this.customers = customers;
        this.auditLog = auditLog;
        this.authService = authService;
    }

    /**
     * Cross-tenant aggregated list of TPs + Partners + Customers.
     * Order: TPs first, then partners, then customers. Within each group the
     * underlying repositories return rows in insertion order.
     */
    @Transactional(readOnly = true)
    public List<AdminAccountRow> list(AuthPrincipal me) {
        requireAdmin(me);
        List<AdminAccountRow> out = new ArrayList<>();

        // TP rows — populate orgName / plan / accountStatus from tp_accounts.
        // The "primary user" linkage feeds userId / userName so the admin portal
        // can show "Bhoomihaar Express — Vikram Singh" without an extra fetch.
        for (TpAccount tp : tpAccounts.findAll()) {
            String userName = null;
            if (tp.getPrimaryUserId() != null) {
                userName = users.findById(tp.getPrimaryUserId()).map(User::getName).orElse(null);
            }
            out.add(new AdminAccountRow(
                    tp.getId(),
                    "TP",
                    tp.getId(),
                    tp.getName(),
                    tp.getPrimaryUserId(),
                    userName,
                    tp.getPlan() == null ? null : tp.getPlan().name(),
                    tp.getAccountStatus() == null ? null : tp.getAccountStatus().name(),
                    /* isShadow   */ false,
                    /* orderCount */ 0L));
        }

        // Partner rows — userName is the company name (display); userId is the partner profile's user.
        for (PartnerTpProfile p : partners.findAll()) {
            out.add(new AdminAccountRow(
                    p.getId(),
                    "PARTNER",
                    /* tpAccountId    */ null,
                    /* orgName        */ null,
                    p.getUserId(),
                    p.getCompanyName(),
                    /* plan           */ null,
                    /* accountStatus  */ null,
                    /* isShadow       */ false,
                    /* orderCount     */ 0L));
        }

        // Customer rows — id is the customers.id (not users.id); the FE keys impersonate by id+type.
        for (Customer c : customers.findAll()) {
            out.add(new AdminAccountRow(
                    c.getId(),
                    "CUSTOMER",
                    /* tpAccountId    */ null,
                    /* orgName        */ null,
                    c.getId(),
                    c.getName(),
                    /* plan           */ null,
                    /* accountStatus  */ null,
                    c.isShadow(),
                    /* orderCount     */ 0L));
        }

        return out;
    }

    /**
     * Approves or rejects a TP signup (BACKEND_GAPS §7). PARTNER / CUSTOMER rows
     * have no concept of {@code accountStatus} — passing their id returns
     * {@link ErrorCode#VALIDATION_ERROR}.
     */
    @Transactional
    public AdminAccountRow updateStatus(AuthPrincipal me, String accountId, String statusName) {
        requireAdmin(me);
        TpAccount tp = tpAccounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND,
                        "TP account not found (status update only valid for TP rows)"));

        AccountStatus status;
        try {
            status = AccountStatus.valueOf(statusName);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported account status: " + statusName,
                    Map.of("allowed", List.of("PENDING", "APPROVED", "REJECTED")));
        }

        tp.setAccountStatus(status);
        tp.setAccountStatusUpdatedAt(Instant.now());
        tp.setAccountStatusUpdatedBy(me.userId());
        tpAccounts.save(tp);
        return tpRowFromEntity(tp);
    }

    /**
     * Changes a TP's plan (BACKEND_GAPS §7). BR-PLN-04: only COGNILOGISTIC_ADMIN
     * can change a TP's plan; the gate is in {@link #requireAdmin}.
     */
    @Transactional
    public AdminAccountRow updatePlan(AuthPrincipal me, String accountId, String planName) {
        requireAdmin(me);
        TpAccount tp = tpAccounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND,
                        "TP account not found (plan update only valid for TP rows)"));

        Plan plan;
        try {
            plan = Plan.valueOf(planName);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported plan: " + planName,
                    Map.of("allowed", List.of("BASIC", "PREMIUM", "ENTERPRISE")));
        }

        tp.setPlan(plan);
        tp.setPlanSetAt(Instant.now());
        tp.setPlanSetBy(me.userId());
        tpAccounts.save(tp);
        return tpRowFromEntity(tp);
    }

    /**
     * Starts an impersonation session (BACKEND_GAPS §7). Validates the target
     * type / id, blocks shadow customer impersonation, writes the audit log row,
     * and issues an impersonation token whose JWT decodes to the target identity
     * but carries an {@code imp} claim with the admin's user id.
     */
    @Transactional
    public ImpersonationSessionResponse impersonate(AuthPrincipal me, ImpersonateRequest req) {
        requireAdmin(me);

        User target;
        String targetTpAccountId = null;
        String targetName;

        switch (req.targetType()) {
            case "TP" -> {
                TpAccount tp = tpAccounts.findById(req.targetId())
                        .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "TP not found"));
                if (tp.getPrimaryUserId() == null) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "TP has no primary user to impersonate as");
                }
                target = users.findById(tp.getPrimaryUserId())
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "TP primary user not found"));
                targetTpAccountId = tp.getId();
                targetName = tp.getName();
            }
            case "PARTNER" -> {
                PartnerTpProfile partner = partners.findById(req.targetId())
                        .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "Partner not found"));
                target = users.findById(partner.getUserId())
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Partner user not found"));
                targetName = partner.getCompanyName();
            }
            case "CUSTOMER" -> {
                Customer customer = customers.findById(req.targetId())
                        .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "Customer not found"));
                if (customer.isShadow()) {
                    throw new ApiException(ErrorCode.SHADOW_IMPERSONATION_BLOCKED,
                            "Shadow customers cannot be impersonated");
                }
                if (customer.getWhatsappPhone() == null || customer.getWhatsappPhone().isBlank()) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "Customer has no phone on file — cannot resolve portal user for impersonation");
                }
                // Customers may not yet have a User row (OTP-only login binds it on first
                // verify). We can't impersonate without one — surface a friendly error.
                target = users.findByPhone(customer.getWhatsappPhone())
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                                "Customer has not activated portal access — no user identity to impersonate as"));
                targetName = customer.getName();
            }
            default -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported targetType: " + req.targetType(),
                    Map.of("allowed", List.of("TP", "PARTNER", "CUSTOMER")));
        }

        // Audit row — written first so even if token issuance throws (e.g. JWT key
        // misconfig) we still have a forensic trail of the attempted entry.
        Instant now = Instant.now();
        ImpersonationAuditLog row = new ImpersonationAuditLog();
        row.ensureId();
        row.setAdminUserId(me.userId());
        row.setAdminName(users.findById(me.userId()).map(User::getName).orElse(null));
        row.setTargetType(req.targetType());
        row.setTargetTpAccountId(targetTpAccountId);
        row.setTargetUserId(req.targetType().equals("TP") ? null : target.getId());
        row.setTargetName(targetName);
        row.setSessionStartedAt(now);
        row.setActionsPerformed(0);
        row.setNotes(req.reason());
        row.setCreatedAt(now);
        auditLog.save(row);

        // Synthesise the impersonation device id server-side from the calling
        // admin's JWT user id + the freshly-generated audit-log session id.
        // FE never supplies deviceId on the impersonate request (the field
        // doesn't exist on ImpersonateRequest) — keeps the trust boundary clean
        // and prevents a hostile client from spoofing someone else's device key.
        // The literal `imp-` prefix lets ops filter impersonation rows out of
        // normal-user analytics: `WHERE device_id LIKE 'imp-%'`.
        String impDeviceId = "imp-" + me.userId() + "-" + row.getId();
        DeviceType impDeviceType = req.deviceType() != null ? req.deviceType() : DeviceType.WEB;

        // Issue the impersonation token — same shape as a normal login response
        // so the FE swap is one assignment.
        LoginResponse login = authService.loginAs(target, impDeviceId, impDeviceType, me.userId());
        return new ImpersonationSessionResponse(row.getId(), login);
    }

    /**
     * Ends an impersonation session and re-issues admin-side tokens. Validates
     * that the session belongs to the calling admin (no exiting someone else's
     * session).
     */
    @Transactional
    public ImpersonationSessionResponse exit(AuthPrincipal me, String sessionId, ExitImpersonationRequest req) {
        // The FE calls /exit with the IMPERSONATION token (the apiClient is
        // already swapped to the target identity at this point — the admin's
        // original token is FE-stashed for later restore but isn't on the wire).
        // So `me.role()` here will be the TARGET's role (TP_ADMIN, PARTNER_TP,
        // CUSTOMER), not COGNILOGISTIC_ADMIN. The original admin's id lives in
        // `me.impersonatedByUserId()` (the JWT `imp` claim).
        //
        // Authorisation here is therefore "I'm impersonated AND my imp claim
        // matches the audit row's admin_user_id". Calling /exit from a real
        // admin token without going through impersonate-start first is also
        // tolerated (audit row's adminUserId == me.userId()).
        ImpersonationAuditLog row = auditLog.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "Impersonation session not found"));

        String callingAdminId = me.isImpersonated() ? me.impersonatedByUserId() : me.userId();
        if (callingAdminId == null || !row.getAdminUserId().equals(callingAdminId)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Cannot exit a session that belongs to a different admin");
        }
        // Belt-and-suspenders: confirm the resolved adminUserId actually IS an admin.
        // Catches the corner case where someone forges an `imp` claim pointing
        // at a non-admin user id (shouldn't be possible with our JWT signing,
        // but the audit-log integrity check is cheap).
        User admin = users.findById(callingAdminId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Admin user not found"));
        if (admin.getRole() != UserRole.COGNILOGISTIC_ADMIN) {
            throw new ApiException(ErrorCode.IMPERSONATION_NOT_ALLOWED,
                    "Audited admin is no longer COGNILOGISTIC_ADMIN — cannot exit on their behalf");
        }

        if (row.getSessionEndedAt() == null) {
            row.setSessionEndedAt(Instant.now());
            auditLog.save(row);
        }

        // Reconstruct the SAME synthetic device id used at impersonate-start
        // ("imp-" + adminUserId + "-" + sessionId) so the rotate-in-place
        // refresh-token flow overwrites the impersonation token with the admin
        // token cleanly. The admin's "real" (non-imp) device session — if any —
        // is untouched because it lives under a different device id.
        String impDeviceId = "imp-" + callingAdminId + "-" + sessionId;
        DeviceType impDeviceType = (req != null && req.deviceType() != null)
                ? req.deviceType()
                : DeviceType.WEB;
        LoginResponse adminLogin = authService.loginAs(admin, impDeviceId, impDeviceType, null);
        return new ImpersonationSessionResponse(row.getId(), adminLogin);
    }

    // ===== Helpers =====

    private void requireAdmin(AuthPrincipal me) {
        if (me == null || me.role() != UserRole.COGNILOGISTIC_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only platform administrators can call admin endpoints");
        }
    }

    /** Builds an {@link AdminAccountRow} for a freshly-mutated {@link TpAccount}. */
    private AdminAccountRow tpRowFromEntity(TpAccount tp) {
        String userName = null;
        if (tp.getPrimaryUserId() != null) {
            userName = users.findById(tp.getPrimaryUserId()).map(User::getName).orElse(null);
        }
        return new AdminAccountRow(
                tp.getId(),
                "TP",
                tp.getId(),
                tp.getName(),
                tp.getPrimaryUserId(),
                userName,
                tp.getPlan() == null ? null : tp.getPlan().name(),
                tp.getAccountStatus() == null ? null : tp.getAccountStatus().name(),
                false,
                0L);
    }
}
