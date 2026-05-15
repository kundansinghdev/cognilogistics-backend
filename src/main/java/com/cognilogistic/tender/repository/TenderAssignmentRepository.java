package com.cognilogistic.tender.repository;

import com.cognilogistic.tender.model.TenderAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TenderAssignment}.
 *
 * <p>1:1 with {@code tenders}; {@code findById(tenderId)} is the only read path.
 */
@Repository
public interface TenderAssignmentRepository extends JpaRepository<TenderAssignment, String> {
}
