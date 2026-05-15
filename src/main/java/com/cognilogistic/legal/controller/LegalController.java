package com.cognilogistic.legal.controller;

import com.cognilogistic.legal.dto.LegalVersionsDto;
import com.cognilogistic.legal.service.LegalDocVersionService;
import com.cognilogistic.platform.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Public endpoints under {@code /api/v1/legal/*} that expose the current
 * legal-doc version strings to the front-end.
 *
 * <p>No auth required (added to {@code SecurityConfig}'s permit-all list).
 * Front-end calls {@code GET /api/v1/legal/current-versions} once on signup-page
 * mount and stashes the version strings in memory; they get re-sent on
 * {@code POST /auth/setup-pin}.
 *
 * <p><strong>Caching:</strong> response carries {@code Cache-Control: public,
 * max-age=60}. Versions change rarely (only when legal republishes a doc), so
 * a minute of staleness is acceptable. Reduces load on this endpoint when
 * many tabs are open in dev / QA.
 *
 * <p><strong>Rate limiting:</strong> not yet implemented at this controller —
 * the global rate-limiter (when added) will cover public endpoints. Until
 * then, the response is cheap enough (one indexed read of 2 rows) that abuse
 * is not a meaningful concern in UAT.
 */
@Tag(name = "Legal (public)", description = "Published Terms/Privacy version strings for signup. No JWT.")
@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private final LegalDocVersionService versions;

    public LegalController(LegalDocVersionService versions) {
        this.versions = versions;
    }

    /**
     * Returns the currently published version of each legal document.
     *
     * <p>Wraps the DTO in {@link ResponseEntity} (rather than the usual bare
     * {@code ApiResponse}) so we can attach a {@code Cache-Control} header.
     * The body shape is still the canonical {@code ApiResponse} envelope.
     *
     * @return 200 with the version DTO; never 4xx (no input to validate)
     */
    @GetMapping("/current-versions")
    public ResponseEntity<ApiResponse<LegalVersionsDto>> currentVersions() {
        ApiResponse<LegalVersionsDto> body = ApiResponse.ok(versions.currentVersions());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }
}
