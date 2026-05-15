package com.cognilogistic.user.controller;

import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.dto.CreateOfficeRequest;
import com.cognilogistic.user.dto.OfficeResponseDto;
import com.cognilogistic.user.dto.UpdateOfficeRequest;
import com.cognilogistic.user.service.OfficeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for branch office management at {@code /api/v1/offices}.
 *
 * <p>Access rules (BR-OFF-05):
 * <ul>
 *   <li>{@code GET /offices} and {@code GET /offices/{id}} — open to all authenticated TP roles.</li>
 *   <li>{@code POST}, {@code PATCH}, {@code DELETE} — restricted to {@link UserRole#TP_ADMIN}
 *       (the account admin). Returns 403 {@code FORBIDDEN} for other roles.</li>
 * </ul>
 *
 * <p>All responses use the {@link ApiResponse} envelope. Business rule violations are thrown
 * as {@link ApiException} and handled by the global {@code GlobalExceptionHandler}.
 *
 * <p>Request logging follows the same {@code [ENTRY]} / {@code [EXIT]} pattern as
 * {@link com.cognilogistic.auth.controller.AuthController}.
 */
@Tag(name = "Offices", description = "Branch offices for TP account. JWT.")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
@RestController
@RequestMapping("/api/v1/offices")
public class OfficeController {

    private static final Logger log = LoggerFactory.getLogger(OfficeController.class);

    private final OfficeService officeService;

    public OfficeController(OfficeService officeService) {
        this.officeService = officeService;
    }

    @GetMapping
    public ApiResponse<List<OfficeResponseDto>> list(@CurrentUser AuthPrincipal me) {
        requireTpAccount(me);
        log.info("[ENTRY] listOffices | userId={} | tpAccountIdSuffix={}",
                me.userId(), suffixId(me.tpAccountId()));
        return ControllerRequestLogging.withExitLog(OfficeController.class, "listOffices",
                () -> officeService.list(me.tpAccountId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OfficeResponseDto> getById(@PathVariable String id,
                                                  @CurrentUser AuthPrincipal me) {
        requireTpAccount(me);
        log.info("[ENTRY] getOffice | id={} | userId={}", id, me.userId());
        return ControllerRequestLogging.withExitLog(OfficeController.class, "getOffice",
                () -> officeService.getById(me.tpAccountId(), id));
    }

    @GetMapping("/dropdown")
    public ApiResponse<List<OfficeResponseDto>> dropdown(@CurrentUser AuthPrincipal me) {
        requireTpAccount(me);
        log.info("[ENTRY] listOfficesDropdown | userId={}", me.userId());
        return ControllerRequestLogging.withExitLog(OfficeController.class, "listOfficesDropdown",
                () -> officeService.listForDropdown(me.tpAccountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OfficeResponseDto> create(@Valid @RequestBody CreateOfficeRequest req,
                                                  @CurrentUser AuthPrincipal me) {
        requireAdmin(me);
        log.info("[ENTRY] createOffice | userId={} | codeLen={} | nameLen={}",
                me.userId(),
                req.code() != null ? req.code().length() : 0,
                req.name() != null ? req.name().length() : 0);
        return ControllerRequestLogging.withExitLog(OfficeController.class, "createOffice",
                () -> officeService.create(me.tpAccountId(), req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<OfficeResponseDto> update(@PathVariable String id,
                                                  @Valid @RequestBody UpdateOfficeRequest req,
                                                  @CurrentUser AuthPrincipal me) {
        requireAdmin(me);
        log.info("[ENTRY] updateOffice | id={} | userId={}", id, me.userId());
        return ControllerRequestLogging.withExitLog(OfficeController.class, "updateOffice",
                () -> officeService.update(me.tpAccountId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @CurrentUser AuthPrincipal me) {
        requireAdmin(me);
        log.info("[ENTRY] deleteOffice | id={} | userId={}", id, me.userId());
        ControllerRequestLogging.withExitLogVoid(OfficeController.class, "deleteOffice",
                () -> officeService.delete(me.tpAccountId(), id));
    }

    private static String suffixId(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    private void requireTpAccount(AuthPrincipal me) {
        if (me == null || me.tpAccountId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not associated with a TP account");
        }
    }

    private void requireAdmin(AuthPrincipal me) {
        requireTpAccount(me);
        if (me.role() != UserRole.TP_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only TP_ADMIN users can create, edit, or delete offices");
        }
    }
}
