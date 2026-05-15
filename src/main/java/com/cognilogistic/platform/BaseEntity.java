package com.cognilogistic.platform;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Abstract JPA mapped superclass that provides automatic audit timestamps for all entities.
 *
 * <p>All persistent entities in the platform (except log-only entities with a manual
 * {@code @CreatedDate}) extend this class to inherit {@code created_at} and {@code updated_at}
 * columns that are maintained by Spring Data's JPA auditing infrastructure
 * (enabled globally via {@code @EnableJpaAuditing} on the application class).
 *
 * <p>{@code created_at} is set once at INSERT time and is never updated thereafter
 * ({@code updatable = false}). {@code updated_at} is refreshed on every UPDATE.
 *
 * <p>For a Spring/JPA junior: {@code @MappedSuperclass} means "these fields/columns
 * are inherited by subclass entities, but no table is created for this class itself."
 * {@code @EntityListeners(AuditingEntityListener.class)} hooks the Spring Data
 * auditing listener into JPA's persist/update events so the timestamps populate
 * automatically — no service code needs to set them.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    /** Timestamp when the entity row was first inserted. Immutable after creation. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp of the most recent update to the entity row. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
