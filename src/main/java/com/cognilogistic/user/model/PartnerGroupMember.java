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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for the {@code partner_group_members} junction table — partner ↔
 * group membership.
 *
 * <p>Composite key {@code (group_id, partner_id)} via {@link IdClass}. CASCADE
 * on group delete (membership rows go away when the group does); RESTRICT on
 * partner delete (we never hard-delete partners — they go inactive instead).
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 639–647;
 * created by migration {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
 */
@Entity
@Table(name = "partner_group_members")
@IdClass(PartnerGroupMember.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartnerGroupMember {

    @Id
    @Column(name = "group_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String groupId;

    @Id
    @Column(name = "partner_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String partnerId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    /** Composite-key class — required by JPA's {@link IdClass} mechanism. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private String groupId;
        private String partnerId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return Objects.equals(groupId, other.groupId) && Objects.equals(partnerId, other.partnerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, partnerId);
        }
    }
}
