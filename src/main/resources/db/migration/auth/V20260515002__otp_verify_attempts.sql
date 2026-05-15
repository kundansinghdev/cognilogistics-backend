-- Per-OTP-row failed verify counter (brute-force mitigation within TTL window).
ALTER TABLE otp_log
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0 AFTER verified;
