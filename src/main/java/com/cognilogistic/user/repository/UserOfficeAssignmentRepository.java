package com.cognilogistic.user.repository;

import com.cognilogistic.user.model.UserOfficeAssignment;
import com.cognilogistic.user.model.UserOfficeAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link UserOfficeAssignment} — the join table linking
 * TP_TRANSPORT_MANAGER users to the offices they're authorised to operate.
 *
 * <p>The PK type is the composite {@link UserOfficeAssignmentId}, not a single column —
 * see {@link UserOfficeAssignment} for the v5.0 composite-PK design rationale.
 *
 * <p>The two derived finders below are mirror images:
 * <ul>
 *   <li>{@link #findByUserId(String)} answers "which offices can user X access?"
 *       — the LoginResponse builder calls this to populate the user's office list.</li>
 *   <li>{@link #findByOfficeId(String)} answers "who is assigned to office Y?"
 *       — the Admin Portal's office-detail tab uses this.</li>
 * </ul>
 */
public interface UserOfficeAssignmentRepository extends JpaRepository<UserOfficeAssignment, UserOfficeAssignmentId> {

    /**
     * Returns all office assignments for a given user.
     * Used to list which offices a branch user can see and act on.
     *
     * @param userId the user's ID
     * @return all UserOfficeAssignment rows for that user
     */
    List<UserOfficeAssignment> findByUserId(String userId);

    /**
     * Returns all user assignments for a given office.
     * Used when listing users who are members of an office.
     *
     * @param officeId the office ID
     * @return all UserOfficeAssignment rows for that office
     */
    List<UserOfficeAssignment> findByOfficeId(String officeId);

    /**
     * Checks whether a specific user is assigned to a specific office.
     * Used by {@code OrderService} during fleet confirmation to verify the acting
     * user belongs to the order's assigned office.
     *
     * @param userId   the user to check
     * @param officeId the office to check membership in
     * @return {@code true} if the assignment exists
     */
    boolean existsByUserIdAndOfficeId(String userId, String officeId);
}
