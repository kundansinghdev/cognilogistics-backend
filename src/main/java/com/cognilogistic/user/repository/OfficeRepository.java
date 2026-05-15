package com.cognilogistic.user.repository;

import com.cognilogistic.user.model.Office;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Office} entities.
 *
 * <p>Audit-style {@code [ENTRY]}/{@code [EXIT]} logging for mutations is implemented in
 * {@link com.cognilogistic.user.controller.OfficeController} and
 * {@link com.cognilogistic.user.service.OfficeService}; this interface stays a thin Spring Data projection.
 *
 * <p>All queries are implicitly scoped to a {@code tpAccountId} to prevent cross-account access.
 * The {@link #findByTpAccountIdAndIsActive} method uses the composite index
 * {@code idx_office_active(tp_account_id, is_active)} for fast dropdown population (DD-08).
 */
public interface OfficeRepository extends JpaRepository<Office, String> {

    /**
     * Returns all offices belonging to the given TP account (any active status).
     * Used by the office listing endpoint to show all offices including inactive ones.
     *
     * @param tpAccountId the owning TP account ID
     * @return all offices for that account
     */
    List<Office> findByTpAccountId(String tpAccountId);

    /**
     * Returns offices filtered by active status, scoped to a TP account.
     * Use with {@code isActive = true} for the order-assignment dropdown (BR-OFF-04, DD-08).
     * Uses the composite index {@code idx_office_active} for efficiency.
     *
     * @param tpAccountId the owning TP account ID
     * @param isActive    {@code true} for active offices only; {@code false} for inactive only
     * @return matching offices
     */
    List<Office> findByTpAccountIdAndIsActive(String tpAccountId, boolean isActive);

    /**
     * Looks up a single office by ID scoped to a TP account.
     * Returns empty if the office does not exist or belongs to a different account.
     *
     * @param id          the office primary key
     * @param tpAccountId the caller's TP account ID
     * @return the office if found and owned by the given account
     */
    Optional<Office> findByIdAndTpAccountId(String id, String tpAccountId);

    /**
     * Checks whether a code is already taken within a TP account (BR-OFF-02).
     * Called on POST to enforce the unique-code constraint before insert.
     *
     * @param code        the normalised (uppercase) office code to check
     * @param tpAccountId the TP account scope
     * @return {@code true} if the code is already in use
     */
    boolean existsByCodeAndTpAccountId(String code, String tpAccountId);

    /**
     * Checks whether a code is already taken within a TP account, excluding the given office ID.
     * Called on PATCH to allow updating an office's code to itself (no false conflict).
     *
     * @param code        the normalised (uppercase) office code to check
     * @param tpAccountId the TP account scope
     * @param id          the office being updated (excluded from the uniqueness check)
     * @return {@code true} if another office in the account already uses this code
     */
    boolean existsByCodeAndTpAccountIdAndIdNot(String code, String tpAccountId, String id);

    /**
     * Same display identity (name + city + state) under one TP — blocks accidental duplicate branches.
     */
    boolean existsByTpAccountIdAndNameIgnoreCaseAndCityIgnoreCaseAndStateIgnoreCase(
            String tpAccountId, String name, String city, String state);

    boolean existsByTpAccountIdAndNameIgnoreCaseAndCityIgnoreCaseAndStateIgnoreCaseAndIdNot(
            String tpAccountId, String name, String city, String state, String id);
}
