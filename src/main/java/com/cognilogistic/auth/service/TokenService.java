package com.cognilogistic.auth.service;

import com.cognilogistic.auth.model.DeviceType;
import com.cognilogistic.auth.model.RefreshToken;
import com.cognilogistic.auth.model.User;
import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.repository.RefreshTokenRepository;
import com.cognilogistic.order.model.Customer;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Manages the JWT access-token lifecycle and the opaque refresh-token database records.
 *
 * <p>Access tokens are signed HMAC-SHA256 JWTs containing: userId (subject), phone, role,
 * tpAccountId (as claim {@code tp}), and device_type.
 *
 * <p>Refresh tokens are random 32-byte Base64url strings stored as SHA-256 hex digests in the
 * {@code refresh_tokens} table. The raw string is returned to the client once and never stored.
 *
 * <p>Token rotation: each call to {@code issueRefreshToken} revokes all prior active tokens
 * for the same user+device, enforcing a single active session per device.
 */
@Service
public class TokenService {

    private final AuthProperties props;
    private final RefreshTokenRepository refreshTokens;
    private final SecretKey jwtKey;

    public TokenService(AuthProperties props, RefreshTokenRepository refreshTokens) {
        this.props = props;
        this.refreshTokens = refreshTokens;
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes");
        }
        this.jwtKey = Keys.hmacShaKeyFor(secret);
    }

    /**
     * Builds and signs a JWT access token for a TP user.
     * The token TTL is driven by device type: MOBILE gets a longer TTL than WEB.
     *
     * @param user       the authenticated TP user
     * @param deviceType WEB or MOBILE; drives TTL selection
     * @return compact signed JWT string
     */
    public String issueAccessToken(User user, DeviceType deviceType) {
        return issueAccessToken(user, deviceType, null);
    }

    /**
     * Builds a JWT for an admin-impersonation session (BACKEND_GAPS §7).
     *
     * <p>The token is issued for the {@link User#getId() target user's identity}
     * (so downstream tenancy / role checks resolve as if the target were calling)
     * but carries an extra {@code imp} claim with the COGNILOGISTIC_ADMIN's user
     * id. Audit hooks read {@code imp} to stamp impersonation context on every
     * mutation performed during the session.
     *
     * @param user                  the impersonated (target) user
     * @param deviceType            WEB or MOBILE — drives TTL selection
     * @param impersonatedByUserId  the admin's user id; {@code null} for regular
     *                              (non-impersonation) sessions
     * @return a compact signed JWT
     */
    public String issueAccessToken(User user, DeviceType deviceType, String impersonatedByUserId) {
        Instant now = Instant.now();
        int ttlMin = deviceType == DeviceType.MOBILE
                ? props.jwt().mobileAccessTtlMinutes()
                : props.jwt().webAccessTtlMinutes();
        Instant exp = now.plus(Duration.ofMinutes(ttlMin));
        // SCHEMA: user.id is a CHAR(36) UUID string. JWT.sub is a string by spec, so we
        // pass it through directly. JwtAuthFilter on the receive side keeps it as String.
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("phone", user.getPhone())
                .claim("role", user.getRole().name())
                // tp claim: empty string when user is not bound to a TP (PARTNER_TP, COGNILOGISTIC_ADMIN).
                // Empty-string sentinel chosen over null because Jackson's claim serialiser handles it
                // uniformly across clients; downstream JwtAuthFilter normalises "" → null.
                .claim("tp", user.getTpAccountId() == null ? "" : user.getTpAccountId())
                .claim("ptp", user.getPartnerTpProfileId() == null ? "" : user.getPartnerTpProfileId())
                .claim("device_type", deviceType.name())
                // imp claim: present only on impersonation sessions. Empty-string sentinel keeps the
                // claim shape uniform; JwtAuthFilter normalises "" → null when building AuthPrincipal.
                .claim("imp", impersonatedByUserId == null ? "" : impersonatedByUserId)
                .signWith(jwtKey)
                .compact();
    }

    /**
     * Builds and signs a CUSTOMER-role JWT for an authenticated customer-portal user.
     * The subject is the Customer entity ID (not User.id). TTL uses the webAccessTtlMinutes setting.
     *
     * @param customer the portal customer entity after successful OTP verification
     * @return compact signed JWT string with role=CUSTOMER
     */
    public String issuePortalAccessToken(Customer customer) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.jwt().webAccessTtlMinutes()));
        // Portal access tokens carry the Customer entity id as `sub` — the customer hasn't
        // necessarily been linked to a `users` row yet (that happens at activation).
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(customer.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("phone", customer.getWhatsappPhone() == null ? "" : customer.getWhatsappPhone())
                .claim("role", UserRole.CUSTOMER.name())
                .claim("tp", customer.getCreatedByTp() == null ? "" : customer.getCreatedByTp())
                .claim("company_customer_id", customer.getId())
                .signWith(jwtKey)
                .compact();
    }

    /**
     * Parses and validates a JWT access token, returning the full signed claims object.
     * Called by {@link JwtAuthFilter} on every authenticated request.
     *
     * @param token the compact JWT string from the {@code Authorization: Bearer ...} header
     * @return the validated claims; never returns null
     * @throws com.cognilogistic.platform.api.ApiException with UNAUTHORIZED if the signature
     *         is invalid, the token is expired, or parsing fails for any reason
     */
    public Jws<Claims> parseAccessToken(String token) {
        try {
            return Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(token);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }
    }

    /**
     * Issues a new refresh token for the given user+device pair, revoking any prior active tokens
     * for the same device first (single-session-per-device enforcement).
     *
     * @param user       the authenticated user
     * @param deviceId   the client-supplied stable device identifier
     * @param deviceType WEB or MOBILE
     * @return an {@link IssuedRefresh} containing both the raw token (for the client) and
     *         the persisted entity (for metadata like expiry)
     */
    @Transactional
    public IssuedRefresh issueRefreshToken(User user, String deviceId, DeviceType deviceType) {
        // Schema's UNIQUE KEY uq_rt_user_device (user_id, device_id) covers ALL rows
        // for that pair regardless of revoked_at. So the previous "revoke-then-insert"
        // strategy collided on the second login from the same device:
        //   reset-pin/set / login #1 → INSERT row
        //   login #2 same device → revoke row (UPDATE revoked_at) but the unique slot
        //                          is still occupied → INSERT collides with 1062
        //                          DataIntegrityViolation → 500
        //
        // Fix: rotate-in-place. If a row exists for (user, device), overwrite its
        // hash + expiry + revoked_at=null instead of inserting a new row. This
        // preserves the single-active-token-per-device invariant that the unique
        // key encodes. We lose per-device-rotation history, but the audit log /
        // SecurityEvent stream can capture rotations separately if needed.
        Instant now = Instant.now();
        String raw = Hashing.randomBase64Url(32);
        Instant expiresAt = now.plus(Duration.ofDays(props.jwt().refreshTtlDays()));
        String newHash = Hashing.sha256Hex(raw);

        // findByUserIdAndDeviceId returns the at-most-one row (DB unique guards the cardinality).
        RefreshToken rt = refreshTokens.findByUserIdAndDeviceId(user.getId(), deviceId).stream()
                .findFirst()
                .orElseGet(() -> {
                    RefreshToken fresh = new RefreshToken();
                    // SCHEMA: id is CHAR(36) UUID — generate server-side.
                    fresh.setId(UUID.randomUUID().toString());
                    fresh.setUserId(user.getId());
                    return fresh;
                });
        rt.setTokenHash(newHash);
        rt.setDeviceId(deviceId);
        rt.setDeviceType(deviceType);
        rt.setExpiresAt(expiresAt);
        // Always reset revoked_at — a freshly-rotated token is, by definition, active.
        rt.setRevokedAt(null);
        refreshTokens.save(rt);
        return new IssuedRefresh(raw, rt);
    }

    /**
     * Looks up and validates the refresh token, marks it revoked, and returns the entity
     * so the caller can determine the device and user for session continuation.
     *
     * @param rawToken the unhashed refresh token string from the client
     * @return the revoked token entity (caller uses it to issue a new token pair)
     * @throws com.cognilogistic.platform.api.ApiException with UNAUTHORIZED if not found or not active
     */
    @Transactional
    public RefreshToken consumeRefreshToken(String rawToken) {
        String hash = Hashing.sha256Hex(rawToken);
        RefreshToken rt = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token not found"));
        if (!rt.isActive(Instant.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token revoked or expired");
        }
        rt.setRevokedAt(Instant.now());
        return rt;
    }

    /**
     * Revokes all active refresh tokens for a user across every device.
     * Called during PIN reset to invalidate all existing sessions.
     *
     * @param userId the user whose tokens should be revoked
     */
    @Transactional
    public void revokeAllForUser(String userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Carries the result of a refresh-token issuance: the raw string to return to the client
     * and the persisted entity which holds metadata such as {@code expiresAt}.
     */
    public record IssuedRefresh(String rawToken, RefreshToken stored) {}
}
