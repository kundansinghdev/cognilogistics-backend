package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.AuthTempToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTempTokenRepository extends JpaRepository<AuthTempToken, String> {

    Optional<AuthTempToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM AuthTempToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
