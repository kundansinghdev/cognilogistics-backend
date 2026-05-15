package com.cognilogistic.legal.service;

import com.cognilogistic.legal.dto.LegalVersionsDto;
import com.cognilogistic.legal.model.DocType;
import com.cognilogistic.legal.model.LegalDocVersion;
import com.cognilogistic.legal.repository.LegalDocVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only service for the {@code legal_doc_versions} table.
 *
 * <p>Drives the {@code GET /api/v1/legal/current-versions} endpoint that the FE
 * reads on the signup-screen mount. Update / publish flows for new versions
 * are out of scope for v1 — ops runs raw {@code UPDATE legal_doc_versions
 * SET version = …, published_at = NOW()} (see spec §3.2).
 */
@Service
public class LegalDocVersionService {

    private final LegalDocVersionRepository repository;

    public LegalDocVersionService(LegalDocVersionRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the currently published version of every doc type, packaged as
     * the wire DTO. Reads two rows ({@code TERMS} + {@code PRIVACY}); the
     * service caches nothing in-memory because the FE response is cached at
     * the HTTP layer ({@code Cache-Control: max-age=60}, see
     * {@link com.cognilogistic.legal.controller.LegalController}).
     *
     * @return DTO with terms + privacy version entries
     */
    @Transactional(readOnly = true)
    public LegalVersionsDto currentVersions() {
        Map<DocType, LegalDocVersion> byType = new EnumMap<>(DocType.class);
        for (LegalDocVersion row : repository.findAll()) {
            byType.put(row.getDocType(), row);
        }
        return LegalVersionsDto.from(byType);
    }
}
