package com.cognilogistic.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "auth_temp_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AuthTempToken {

    @Id
    @Column(name = "token", length = 32, nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String token;

    @Column(name = "user_id", length = 36)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String userId;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private TempTokenScope scope;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum TempTokenScope {
        SETUP_PIN,
        RESET_PIN
    }
}
