package com.cognilogistic.platform.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ImpersonationAuditLog}.
 *
 * <p>R6 surface: standard {@code findById} / {@code save}. List queries (admin
 * portal "session history" view) land in a follow-up.
 */
@Repository
public interface ImpersonationAuditLogRepository extends JpaRepository<ImpersonationAuditLog, String> {
}
