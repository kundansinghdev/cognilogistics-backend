package com.cognilogistic.auth.service;

import com.cognilogistic.auth.model.AuthTempToken;
import com.cognilogistic.auth.repository.AuthTempTokenRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Short-lived single-use tokens for cross-endpoint auth flows, persisted in
 * {@code auth_temp_tokens} so signup and PIN-reset work across app instances and restarts.
 */
@Service
public class TempTokenService {

    public enum Scope { SETUP_PIN, RESET_PIN }

    public record Entry(String userId, String phone, Scope scope, Instant expiresAt) {}

    private final AuthTempTokenRepository repo;
    private final AuthProperties props;

    public TempTokenService(AuthTempTokenRepository repo, AuthProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional
    public String issueSetupPinForPhone(String phone) {
        return issue(null, phone, Scope.SETUP_PIN, props.jwt().tempTokenTtlMinutes());
    }

    @Transactional
    public String issueSetupPin(String userId, String phone) {
        return issue(userId, phone, Scope.SETUP_PIN, props.jwt().tempTokenTtlMinutes());
    }

    @Transactional
    public String issueResetPin(String userId, String phone) {
        return issue(userId, phone, Scope.RESET_PIN, props.jwt().resetTokenTtlMinutes());
    }

    private String issue(String userId, String phone, Scope scope, int ttlMin) {
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        AuthTempToken row = new AuthTempToken();
        row.setToken(token);
        row.setUserId(userId);
        row.setPhone(phone);
        row.setScope(toEntityScope(scope));
        row.setExpiresAt(Instant.now().plus(Duration.ofMinutes(ttlMin)));
        row.setCreatedAt(Instant.now());
        repo.save(row);
        return token;
    }

    @Transactional
    public Entry consumeEntry(String token, Scope expectedScope) {
        purgeExpired();
        AuthTempToken row = repo.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Token invalid or already used"));
        repo.delete(row);

        Scope stored = fromEntityScope(row.getScope());
        if (stored != expectedScope) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Token scope mismatch");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Token expired");
        }
        return new Entry(row.getUserId(), row.getPhone(), stored, row.getExpiresAt());
    }

    @Transactional
    public String consume(String token, Scope expectedScope) {
        Entry e = consumeEntry(token, expectedScope);
        if (e.userId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED,
                    "Token has no associated user — use consumeEntry for setup-pin tokens.");
        }
        return e.userId();
    }

    private void purgeExpired() {
        repo.deleteExpiredBefore(Instant.now());
    }

    private static AuthTempToken.TempTokenScope toEntityScope(Scope scope) {
        return scope == Scope.RESET_PIN
                ? AuthTempToken.TempTokenScope.RESET_PIN
                : AuthTempToken.TempTokenScope.SETUP_PIN;
    }

    private static Scope fromEntityScope(AuthTempToken.TempTokenScope scope) {
        return scope == AuthTempToken.TempTokenScope.RESET_PIN ? Scope.RESET_PIN : Scope.SETUP_PIN;
    }
}
