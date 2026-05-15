package com.cognilogistic.tender.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
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
 * JPA entity for the {@code tender_broadcast_groups} junction table — records
 * which {@code partner_groups} a tender was broadcast to.
 *
 * <p>Used by the partner-portal visibility filter (R7): a partner sees a tender
 * iff at least one of their groups appears in this junction. CASCADE on tender
 * delete; RESTRICT on group delete (the API returns 422 if the group still has
 * live tenders, see PR R5).
 *
 * <p><strong>Composite key</strong> — {@code (tender_id, group_id)} via
 * {@link IdClass}. The composite key class {@link Pk} below is the standard JPA
 * pattern for composite primary keys without an artificial surrogate id.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 649–657;
 * created by migration {@code V20260508003__partner_groups_and_tender_broadcast.sql}.
 */
@Entity
@Table(name = "tender_broadcast_groups")
@IdClass(TenderBroadcastGroup.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenderBroadcastGroup {

    @Id
    @Column(name = "tender_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tenderId;

    @Id
    @Column(name = "group_id", length = 36, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String groupId;

    /**
     * Server timestamp at the moment the broadcast row was written. Set by the
     * service (rather than relying on the DB default) so unit tests with a fixed
     * Clock can assert deterministic timestamps.
     */
    @Column(name = "broadcast_at", nullable = false)
    private Instant broadcastAt;

    /**
     * Composite-key class for {@link TenderBroadcastGroup}. Required by JPA's
     * {@link IdClass} mechanism — must define {@code equals} / {@code hashCode}
     * and a no-arg constructor.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private String tenderId;
        private String groupId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return Objects.equals(tenderId, other.tenderId) && Objects.equals(groupId, other.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenderId, groupId);
        }
    }
}
