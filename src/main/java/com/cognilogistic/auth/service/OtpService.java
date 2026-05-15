package com.cognilogistic.auth.service;

import com.cognilogistic.auth.model.OtpLog;
import com.cognilogistic.auth.model.OtpPurpose;
import com.cognilogistic.auth.repository.OtpLogRepository;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain service responsible for OTP lifecycle management: generation, delivery, and verification.
 *
 * <p>OTPs are always 6 digits (configurable via {@code auth.otp.length}).
 * The raw code is never persisted; only its SHA-256 hex digest is stored in {@code otp_log}.
 * Verification compares the submitted code's hash with the stored hash.
 *
 * <p>A resend-cooldown window ({@code auth.otp.resendCooldownSeconds}) prevents SMS flooding.
 */
@Service
public class OtpService {

    private final OtpLogRepository repo;
    private final OtpProvider provider;
    private final AuthProperties props;

    /**
     * Bcrypt encoder for OTP hashing. v5.0 schema specifies bcrypt for {@code otp_hash}
     * (VARCHAR(60), see schema.sql line 109) — defense in depth so leaked DB dumps are
     * useless for replay even though OTPs are short-lived. Cost factor 10 matches
     * {@link com.cognilogistic.auth.service.PinService}.
     */
    private final BCryptPasswordEncoder otpEncoder = new BCryptPasswordEncoder(10);

    public OtpService(OtpLogRepository repo, OtpProvider provider, AuthProperties props) {
        this.repo = repo;
        this.provider = provider;
        this.props = props;
    }

    /**
     * Generates and delivers an OTP, recording a hashed copy in {@code otp_log}.
     * Enforces the resend-cooldown to prevent SMS flooding.
     *
     * @param phone   the target phone number
     * @param purpose the flow context that will be checked again during verification
     * @throws com.cognilogistic.platform.api.ApiException with RATE_LIMITED if a prior OTP
     *         for the same phone+purpose was issued within the cooldown window
     */
    @Transactional
    public void send(String phone, OtpPurpose purpose) {
        Instant now = Instant.now();
        // Resend cooldown — prevent SMS flooding. Compare against the previous OTP's `sentAt`
        // (renamed from `createdAt` in v5.0 schema).
        //
        // UAT BYPASS: when auth.otp.resend-cooldown-seconds is 0 (or below) the cooldown
        // is disabled entirely so smoke testers can re-click "Send OTP" without hitting
        // RATE_LIMITED. Set the env var OTP_RESEND_COOLDOWN_SECONDS=0 (or override via
        // application-local.yml) for the UAT shell. Production keeps the default 60s
        // and re-enables the gate. Per FE team feedback INTEGRATION_LOG §4.
        if (props.otp().resendCooldownSeconds() > 0) {
            repo.findTopByPhoneAndPurposeOrderBySentAtDesc(phone, purpose).ifPresent(prev -> {
                long secondsSince = Duration.between(prev.getSentAt(), now).toSeconds();
                if (secondsSince < props.otp().resendCooldownSeconds()) {
                    throw new ApiException(ErrorCode.RATE_LIMITED,
                            "Wait " + (props.otp().resendCooldownSeconds() - secondsSince) + "s before requesting another OTP");
                }
            });
        }

        String code = resolveOtpCode();
        String delivered = provider.send(phone, code);
        // Twilio Verify case: provider may have generated its own code; hash whatever it returns.
        String toStore = delivered != null ? delivered : code;

        OtpLog log = new OtpLog();
        // SCHEMA: id is CHAR(36) UUID — generate server-side.
        log.setId(UUID.randomUUID().toString());
        log.setPhone(phone);
        log.setPurpose(purpose);
        // SCHEMA: v5.0 specifies bcrypt for otp_hash (VARCHAR(60), schema.sql line 109).
        // Defense in depth: even though OTPs are short-lived, bcrypt makes leaked DB
        // dumps useless for replay. See auth.md §6.4.
        log.setOtpHash(otpEncoder.encode(toStore));
        log.setSentAt(now);
        log.setVerified(false);
        log.setExpiresAt(now.plus(Duration.ofMinutes(props.otp().ttlMinutes())));
        repo.save(log);
    }

    /**
     * Verifies the submitted OTP against the most recent stored hash for the given phone+purpose.
     * Marks the OTP as consumed on success (single-use enforcement).
     *
     * @param phone   the phone number the OTP was sent to
     * @param purpose the purpose that must match the stored OTP row
     * @param code    the 6-digit code submitted by the client
     * @throws com.cognilogistic.platform.api.ApiException with INVALID_OTP if no matching row,
     *         OTP_USED if already consumed, OTP_EXPIRED if past TTL, or INVALID_OTP if the
     *         hash does not match
     */
    @Transactional
    public void verify(String phone, OtpPurpose purpose, String code) {
        // Look up the most recent OTP for this phone+purpose. v5.0 column rename:
        // sortBy `sentAt`, single-use flag is `verified` (was `consumed_at` timestamp).
        OtpLog log = repo.findTopByPhoneAndPurposeOrderBySentAtDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP, "No OTP issued for this phone"));

        if (log.isConsumed()) {
            throw new ApiException(ErrorCode.OTP_USED, "OTP already used");
        }
        Instant now = Instant.now();
        if (log.isExpired(now)) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "OTP expired");
        }

        // bcrypt-compare: matches() handles the salt-aware comparison internally.
        // Constant-time check against timing-attack reconstruction of the hash.
        if (!otpEncoder.matches(code, log.getOtpHash())) {
            int attempts = log.getFailedAttempts() + 1;
            log.setFailedAttempts(attempts);
            repo.save(log);
            if (attempts >= props.otp().maxVerifyAttempts()) {
                log.setVerified(true);
                repo.save(log);
                throw new ApiException(ErrorCode.OTP_USED,
                        "Too many incorrect attempts. Request a new OTP.");
            }
            throw new ApiException(ErrorCode.INVALID_OTP, "OTP did not match");
        }
        // Single-use enforcement: flip `verified` to true. Subsequent verify attempts on
        // the same row return OTP_USED via isConsumed() above.
        log.setVerified(true);
        repo.save(log);
    }

    private String resolveOtpCode() {
        String fixed = props.otp().fixedCode();
        if (fixed != null && !fixed.isBlank()) {
            return fixed.trim();
        }
        return Hashing.numericOtp(props.otp().length());
    }
}
