package com.cognilogistic.legal.repository;

import com.cognilogistic.legal.model.DocType;
import com.cognilogistic.legal.model.LegalDocVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LegalDocVersion}.
 *
 * <p>The PK is {@link DocType} so {@link #findById(Object)} doubles as
 * {@code findByDocType} — there's only ever one current version per doc type.
 */
@Repository
public interface LegalDocVersionRepository extends JpaRepository<LegalDocVersion, DocType> {
}
