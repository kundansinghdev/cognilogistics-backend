package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Company} entities.
 *
 * <p>All queries are scoped by {@code tpAccountId} so companies from one
 * TP account are never visible to another.
 *
 * <p>Structured request logging for reads/writes lives in
 * {@link com.cognilogistic.order.controller.CompanyController} and
 * {@link com.cognilogistic.order.service.CompanyService}; this repository remains a Spring Data façade.
 */
public interface CompanyRepository extends JpaRepository<Company, String> {

    /**
     * Full-text search across company name (case-insensitive) and GSTIN,
     * scoped to the given TP account.
     * When {@code search} is {@code null}, all companies for the TP account are returned.
     *
     * @param tpAccountId the owning TP account ID
     * @param search      optional substring to match against name or GSTIN; {@code null} means no filter
     * @return matching companies ordered alphabetically by name
     */
    @Query("""
            SELECT c FROM Company c
            WHERE c.tpAccountId = :tp
              AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.gstin LIKE CONCAT('%', :search, '%'))
            ORDER BY c.name
            """)
    List<Company> search(@Param("tp") String tpAccountId, @Param("search") String search);

    /**
     * Looks up a company by GSTIN within the given TP account.
     * Used to prevent duplicate GSTIN entries and to link orders to existing companies.
     *
     * @param gstin       the 15-character GSTIN to search for
     * @param tpAccountId the owning TP account ID
     * @return the matching company, or empty if not found
     */
    Optional<Company> findByGstinAndTpAccountId(String gstin, String tpAccountId);

    /**
     * Duplicate GSTIN guard for PATCH — same GSTIN on a different row in this TP account.
     */
    boolean existsByGstinAndTpAccountIdAndIdNot(String gstin, String tpAccountId, String id);

    /**
     * No-GST companies are keyed by legal name within a TP (GSTIN column is NULL; DB unique does not apply).
     */
    boolean existsByTpAccountIdAndNoGstTrueAndNameIgnoreCase(String tpAccountId, String name);

    boolean existsByTpAccountIdAndNoGstTrueAndNameIgnoreCaseAndIdNot(String tpAccountId, String name, String id);
}
