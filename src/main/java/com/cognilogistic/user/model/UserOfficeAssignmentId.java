package com.cognilogistic.user.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary-key class for {@link UserOfficeAssignment}.
 *
 * <p>JPA requires a separate class to represent the composite PK so it can use it for
 * {@code findById}, {@code equals}, and the L2 cache. The fields here mirror exactly
 * the {@code @Id}-annotated fields in {@link UserOfficeAssignment}.
 *
 * <p>{@link Serializable} is required by the JPA spec for {@code @IdClass} types.
 *
 * <p>The {@link EqualsAndHashCode} from Lombok produces the structural equality JPA
 * relies on — two {@code UserOfficeAssignmentId} instances with the same {@code userId}
 * and {@code officeId} are equal regardless of object identity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserOfficeAssignmentId implements Serializable {

    /** CHAR(36) UUID — must match {@link UserOfficeAssignment#getUserId()}. */
    private String userId;

    /** CHAR(36) UUID — must match {@link UserOfficeAssignment#getOfficeId()}. */
    private String officeId;
}
