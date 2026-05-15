package com.cognilogistic.platform.admin;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-portal REST controller (BACKEND_GAPS §7).
 *
 * <p>All endpoints require {@code COGNILOGISTIC_ADMIN}; the role check lives
 * inside {@link AdminService} so unauthenticated callers fall through to the
 * security filter's 401 path and authenticated-but-wrong-role callers receive
 * {@code IMPERSONATION_NOT_ALLOWED} (per API_REFERENCE §14).
 *
 * <p>Mounted at {@code /api/v1/admin/...}. The FE's sticky impersonation banner
 * (mounted in {@code App.tsx}) keys off the {@code isImpersonated} flag returned
 * on the {@link com.cognilogistic.auth.dto.AuthUser} payload.
 */
@Tag(name = "Admin", description = "COGNILOGISTIC_ADMIN: accounts, impersonation.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('COGNILOGISTIC_ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Cross-tenant aggregated list of TPs + Partners + Customers. */
    @GetMapping("/accounts")
    public ApiResponse<List<AdminAccountRow>> listAccounts(@CurrentUser AuthPrincipal me) {
        log.info("[ENTRY] listAccounts | userId={} | role={}", me.userId(), me.role());
        return ControllerRequestLogging.withExitLog(AdminController.class, "listAccounts", () -> adminService.list(me));
    }

    /** Approves / rejects a TP signup (BACKEND_GAPS §7). PARTNER / CUSTOMER ids return 404. */
    @PatchMapping("/accounts/{id}/status")
    public ApiResponse<AdminAccountRow> updateStatus(@CurrentUser AuthPrincipal me,
                                                      @PathVariable String id,
                                                      @Valid @RequestBody AccountStatusUpdateRequest req) {
        log.info("[ENTRY] updateStatus | userId={} | accountId={} | status={}", me.userId(), id, req.status());
        return ControllerRequestLogging.withExitLog(AdminController.class, "updateStatus",
                () -> adminService.updateStatus(me, id, req.status()));
    }

    /** Changes a TP's plan tier (BR-PLN-04 — admin-only). */
    @PatchMapping("/accounts/{id}/plan")
    public ApiResponse<AdminAccountRow> updatePlan(@CurrentUser AuthPrincipal me,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody AccountPlanUpdateRequest req) {
        log.info("[ENTRY] updatePlan | userId={} | accountId={} | plan={}", me.userId(), id, req.plan());
        return ControllerRequestLogging.withExitLog(AdminController.class, "updatePlan",
                () -> adminService.updatePlan(me, id, req.plan()));
    }

    /**
     * Starts an impersonation session. Returns a {@link ImpersonationSessionResponse}
     * with the new {@link com.cognilogistic.auth.dto.LoginResponse} for the target
     * identity and a {@code sessionId} the FE keeps so it can call {@link #exit}.
     */
    @PostMapping("/impersonate")
    public ApiResponse<ImpersonationSessionResponse> impersonate(@CurrentUser AuthPrincipal me,
                                                                  @Valid @RequestBody ImpersonateRequest req) {
        log.info("[ENTRY] impersonate | userId={} | targetType={}", me.userId(), req.targetType());
        return ControllerRequestLogging.withExitLog(AdminController.class, "impersonate", () -> adminService.impersonate(me, req));
    }

    /**
     * Ends an impersonation session and returns admin-side tokens.
     *
     * <p>Body is optional ({@code required = false}). Per BACKEND_GAPS §7 the FE
     * doesn't send one — the exit flow re-derives the synthetic device id
     * server-side from the calling admin + sessionId. If the FE supplies a body
     * with {@code deviceType}, that overrides the {@code WEB} default
     * (forward-compat for future mobile-admin clients).
     */
    @PostMapping("/impersonate/{sessionId}/exit")
    public ApiResponse<ImpersonationSessionResponse> exit(@CurrentUser AuthPrincipal me,
                                                           @PathVariable String sessionId,
                                                           @RequestBody(required = false) ExitImpersonationRequest req) {
        log.info("[ENTRY] exitImpersonation | userId={} | sessionId={}", me.userId(), sessionId);
        return ControllerRequestLogging.withExitLog(AdminController.class, "exitImpersonation",
                () -> adminService.exit(me, sessionId, req));
    }
}
