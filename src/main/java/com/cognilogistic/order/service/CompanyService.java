package com.cognilogistic.order.service;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.integrationclient.gst.GstClient;
import com.cognilogistic.integrationclient.gst.GstLookupResponse;
import com.cognilogistic.order.dto.CompanyDto;
import com.cognilogistic.order.dto.CompanyMasterLookupResponse;
import com.cognilogistic.order.dto.CreateCompanyRequest;
import com.cognilogistic.order.model.Company;
import com.cognilogistic.order.repository.CompanyRepository;
import com.cognilogistic.order.repository.OrderRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Service for company (shipper/consignee) master-data operations.
 *
 * <p>Provides CRUD operations scoped to a TP account, plus a GSTIN lookup that proxies
 * to the GST SPI. Companies are used on GR/LR documents and are not shared across accounts.
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    // Standard Indian GSTIN format: 2-digit state code + 10-char PAN + 1 entity number + Z + 1 check
    private static final String GSTIN_REGEX =
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$";

    private static final Pattern PINCODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CompanyRepository companies;
    private final OrderRepository orders;
    private final GstClient gstClient;

    public CompanyService(CompanyRepository companies, OrderRepository orders, GstClient gstClient) {
        this.companies = companies;
        this.orders = orders;
        this.gstClient = gstClient;
    }

    /**
     * Lists companies for the caller's TP account with optional name/GSTIN filtering.
     *
     * @param me     the authenticated TP user
     * @param search optional search string; applied to name (case-insensitive) and GSTIN
     * @return matching companies ordered by name
     */
    @Transactional(readOnly = true)
    public List<CompanyDto> search(AuthPrincipal me, String search) {
        log.debug("CompanyRepository.search | tpAccountIdSuffix={} | filterPresent={}",
                suffix(me.tpAccountId()), search != null && !search.isBlank());
        List<Company> rows = companies.search(me.tpAccountId(), search);
        log.debug("CompanyRepository.search | rowCount={}", rows.size());
        List<String> ids = rows.stream().map(Company::getId).toList();
        Map<String, Long> orderCounts = loadLinkedOrderCounts(me.tpAccountId(), ids);
        return rows.stream().map(c -> toDto(c, orderCounts.getOrDefault(c.getId(), 0L))).toList();
    }

    /**
     * Retrieves a single company by ID, enforcing TP account scope.
     *
     * @param me the authenticated TP user
     * @param id the company ID
     * @return the company DTO
     * @throws com.cognilogistic.platform.api.ApiException with {@code ORDER_NOT_FOUND} if the
     *         company does not exist or belongs to a different TP account
     */
    @Transactional(readOnly = true)
    public CompanyDto get(AuthPrincipal me, String id) {
        log.debug("CompanyRepository.findById | id={}", id);
        return companies.findById(id)
                .filter(c -> c.getTpAccountId().equals(me.tpAccountId()))
                .map(c -> toDto(c, orders.countByTpAccountIdAndCompanyId(me.tpAccountId(), id)))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Company not found"));
    }

    /**
     * Creates a new company record under the caller's TP account.
     * Validates the GSTIN format if provided.
     *
     * @param me  the authenticated TP user
     * @param req company details; {@code name} required
     * @return the created company DTO
     */
    @Transactional
    public CompanyDto create(AuthPrincipal me, CreateCompanyRequest req) {
        // legalName / name: at least one must be present at create time. PATCH leaves
        // the existing value intact when both are null (handled in update()).
        String legalName = req.effectiveLegalName();
        if (legalName == null || legalName.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "legalName (or legacy 'name') is required");
        }
        log.info("Creating company | userId={} | tpAccountIdSuffix={}", me.userId(), suffix(me.tpAccountId()));

        Company c = new Company();
        c.setTpAccountId(me.tpAccountId());
        // createdByUserId is NOT NULL on the schema; stamp from the JWT subject.
        c.setCreatedByUserId(me.userId());
        c.setActive(true);
        applyFields(c, req);
        validateCompanyEntity(c);
        assertNoDuplicateCompany(me.tpAccountId(), c, null);
        c.ensureId();
        companies.save(c);
        log.info("Company persisted | id={} | tpAccountIdSuffix={}", c.getId(), suffix(me.tpAccountId()));
        return toDto(c, 0L);
    }

    /**
     * Updates a company record. All non-null fields in the request are applied.
     *
     * @param me  the authenticated TP user
     * @param id  the company ID to update
     * @param req updated fields
     * @return the updated company DTO
     */
    @Transactional
    public CompanyDto update(AuthPrincipal me, String id, CreateCompanyRequest req) {
        log.info("Updating company | id={} | userId={}", id, me.userId());
        Company c = companies.findById(id)
                .filter(co -> co.getTpAccountId().equals(me.tpAccountId()))
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Company not found"));
        applyFields(c, req);
        validateCompanyEntity(c);
        assertNoDuplicateCompany(me.tpAccountId(), c, id);
        companies.save(c);
        log.info("Company row saved | id={}", id);
        return toDto(c, orders.countByTpAccountIdAndCompanyId(me.tpAccountId(), id));
    }

    /**
     * Looks up a GSTIN via the GST SPI, with a 600 ms timeout to avoid blocking the UI.
     * Returns empty if the lookup times out or the SPI returns null.
     *
     * @param gstin the 15-character GSTIN to look up
     * @return GST registry details, or empty on timeout or not-found
     */
    public Optional<GstLookupResponse> gstinLookup(String gstin) {
        if (gstin == null || !gstin.matches(GSTIN_REGEX)) {
            throw new ApiException(ErrorCode.INVALID_GSTIN, "Invalid GSTIN format");
        }
        log.debug("GstClient.lookup | gstinSuffix={}", gstin.length() > 4 ? "****" + gstin.substring(gstin.length() - 4) : "****");
        try {
            GstLookupResponse result = CompletableFuture
                    .supplyAsync(() -> gstClient.lookup(gstin))
                    .get(600, TimeUnit.MILLISECONDS);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("GST lookup timed out or failed for GSTIN {}: {}", gstin, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves a GSTIN against the caller's Company Master (active rows only).
     * Used by the TP create-order screen to auto-fill legal name and primary contact phone.
     */
    @Transactional(readOnly = true)
    public Optional<CompanyMasterLookupResponse> lookupMasterByGstin(AuthPrincipal me, String gstin) {
        if (gstin == null || gstin.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "gstin query parameter is required");
        }
        String norm = gstin.trim().toUpperCase(Locale.ROOT);
        if (!norm.matches(GSTIN_REGEX)) {
            throw new ApiException(ErrorCode.INVALID_GSTIN, "Invalid GSTIN format");
        }
        return companies.findByGstinAndTpAccountId(norm, me.tpAccountId())
                .filter(Company::isActive)
                .map(c -> new CompanyMasterLookupResponse(
                        c.getId(),
                        c.getGstin() != null ? c.getGstin().toUpperCase(Locale.ROOT) : norm,
                        c.getName(),
                        c.getContactPhone()));
    }

    /**
     * Applies non-null request fields to the entity. Used by both {@code create} and
     * {@code update} — null on the request means "leave the existing value unchanged"
     * for PATCH, and "use the entity default" for POST.
     *
     * <p>Aliases handled here:
     * <ul>
     *   <li>{@code legalName} ↔ legacy {@code name}</li>
     *   <li>{@code primaryContact*} ↔ legacy {@code contact*}</li>
     * </ul>
     * The service prefers the canonical name when both are supplied (see the
     * {@code effective*} helpers on {@link CreateCompanyRequest}).
     */
    private void applyFields(Company c, CreateCompanyRequest req) {
        String legalName = req.effectiveLegalName();
        if (legalName != null) c.setName(legalName);

        if (req.tradeName() != null) c.setTradeName(req.tradeName());
        if (req.noGst() != null) c.setNoGst(req.noGst());

        // GSTIN clearing logic: when noGst is explicitly set to true and gstin is omitted,
        // we clear any existing GSTIN. When noGst is false/null and gstin is provided,
        // we set it (regex already validated by the caller for create).
        if (req.gstin() != null) c.setGstin(req.gstin());
        if (Boolean.TRUE.equals(req.noGst())) c.setGstin(null);

        if (req.addressLine1() != null) c.setAddressLine1(req.addressLine1());
        if (req.addressLine2() != null) c.setAddressLine2(req.addressLine2());
        if (req.city() != null) c.setCity(req.city());
        if (req.state() != null) c.setState(req.state());
        if (req.pincode() != null) c.setPincode(req.pincode());

        String contactName = req.effectiveContactName();
        if (contactName != null) c.setContactName(contactName);
        String contactPhone = req.effectiveContactPhone();
        if (contactPhone != null) c.setContactPhone(contactPhone);
        String contactEmail = req.effectiveContactEmail();
        if (contactEmail != null) c.setContactEmail(contactEmail);

        if (req.notes() != null) c.setNotes(req.notes());
        if (req.isActive() != null) c.setActive(req.isActive());
    }

    /**
     * Cross-field checks after {@link #applyFields} — GST vs no-GST, formats on the merged entity.
     * Bean Validation on {@link CreateCompanyRequest} already constrains individual fields;
     * this method enforces business invariants that span multiple columns.
     */
    /**
     * Enforces TP-scoped uniqueness: GSTIN rows hit DB {@code uq_company_tp_gstin}; no-GST rows
     * are de-duplicated by legal name (case-insensitive) in application code.
     */
    private void assertNoDuplicateCompany(String tpAccountId, Company c, String excludeCompanyId) {
        if (!c.isNoGst() && c.getGstin() != null && !c.getGstin().isBlank()) {
            String gstin = c.getGstin().trim().toUpperCase();
            boolean dup = excludeCompanyId == null
                    ? companies.findByGstinAndTpAccountId(gstin, tpAccountId).isPresent()
                    : companies.existsByGstinAndTpAccountIdAndIdNot(gstin, tpAccountId, excludeCompanyId);
            if (dup) {
                throw new ApiException(ErrorCode.COMPANY_GSTIN_EXISTS,
                        "A company with this GSTIN is already in your Company Master.");
            }
        }
        if (c.isNoGst() && c.getName() != null && !c.getName().isBlank()) {
            String name = c.getName().trim();
            boolean dup = excludeCompanyId == null
                    ? companies.existsByTpAccountIdAndNoGstTrueAndNameIgnoreCase(tpAccountId, name)
                    : companies.existsByTpAccountIdAndNoGstTrueAndNameIgnoreCaseAndIdNot(
                            tpAccountId, name, excludeCompanyId);
            if (dup) {
                throw new ApiException(ErrorCode.COMPANY_LEGAL_NAME_EXISTS,
                        "A company without GST with this legal name already exists in your Company Master.");
            }
        }
    }

    private void validateCompanyEntity(Company c) {
        if (!c.isNoGst()) {
            if (c.getGstin() == null || c.getGstin().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "GSTIN is required when the company is GST-registered");
            }
        }
        if (c.getGstin() != null && !c.getGstin().isBlank() && !c.getGstin().matches(GSTIN_REGEX)) {
            throw new ApiException(ErrorCode.INVALID_GSTIN, "Invalid GSTIN format");
        }
        String pincode = c.getPincode();
        if (pincode != null && !pincode.isBlank() && !PINCODE_PATTERN.matcher(pincode.trim()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Pincode must be exactly 6 digits");
        }
        String email = c.getContactEmail();
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Contact email must be a valid address");
        }
        String phone = c.getContactPhone();
        if (phone != null && !phone.isBlank() && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Contact phone must be 10–15 digits, optional leading +");
        }
    }

    private static String suffix(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    private Map<String, Long> loadLinkedOrderCounts(String tpAccountId, List<String> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = orders.countOrdersGroupedByCompanyIds(tpAccountId, companyIds);
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            String companyId = (String) row[0];
            long cnt = row[1] == null ? 0L : ((Number) row[1]).longValue();
            out.put(companyId, cnt);
        }
        return out;
    }

    /**
     * Builds the wire DTO with both legacy + canonical names populated identically.
     * The duplication is intentional — see {@link CompanyDto} for the rolling-cutover
     * rationale.
     */
    private CompanyDto toDto(Company c, long linkedOrderCount) {
        return new CompanyDto(
                c.getId(),
                c.getTpAccountId(),
                c.getName(),                         // canonical legalName
                c.getName(),                         // legacy alias name
                c.getTradeName(),
                c.getGstin(),
                c.isNoGst(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getCity(),
                c.getState(),
                c.getPincode(),
                c.getContactName(),                  // canonical primaryContactName
                c.getContactName(),                  // legacy alias contactName
                c.getContactPhone(),                 // canonical primaryContactPhone
                c.getContactPhone(),                 // legacy alias contactPhone
                c.getContactEmail(),                 // canonical primaryContactEmail
                c.getContactEmail(),                 // legacy alias contactEmail
                c.getNotes(),
                c.isActive(),
                linkedOrderCount);
    }
}
