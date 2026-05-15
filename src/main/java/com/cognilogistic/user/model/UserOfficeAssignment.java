package com.cognilogistic.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code user_office_assignments} join table — maps a
 * {@code TP_TRANSPORT_MANAGER} user to one or more branch offices they're authorised
 * to operate.
 *
 * <p>This table is consulted on every order action to enforce office-membership scoping:
 * a transport manager can only confirm fleet / start transit / deliver / cancel orders
 * whose {@code office_id} matches one of their assignments. TP_ADMIN users bypass this
 * check entirely (they see all offices in their TP).
 *
 * <p><strong>v5.0 design (composite PK):</strong> the natural identity of the row is the
 * pair {@code (user_id, office_id)} — there's no information beyond "this user has access
 * to this office." A surrogate id column would just consume an index slot. v5.0 uses
 * a composite PK with {@link IdClass}; see {@link UserOfficeAssignmentId} for the key class.
 *
 * <p><strong>Removed in v5.0:</strong> the legacy {@code id} surrogate column and the
 * {@code created_at} timestamp. The audit trail (who assigned whom, when) lives in
 * {@code audit_logs}, not here.
 *
 * <p><strong>FK CASCADE:</strong> deleting a user OR an office wipes any matching membership
 * rows automatically. Membership is binary — either the row exists (member) or it doesn't.
 */
@Entity
@Table(name = "user_office_assignments")
@IdClass(UserOfficeAssignmentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOfficeAssignment {

    /** CHAR(36) user id. Half of the composite PK; FK to {@code users.id} (CASCADE). */
    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    /** CHAR(36) office id. Other half of the composite PK; FK to {@code offices.id} (CASCADE). */
    @Id
    @Column(name = "office_id", nullable = false, length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String officeId;
}
