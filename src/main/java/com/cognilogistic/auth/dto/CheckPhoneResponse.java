package com.cognilogistic.auth.dto;

/**
 * Response payload for {@code POST /api/v1/auth/check-phone}.
 *
 * @param registered {@code true} if a row exists in {@code users} for this phone.
 * @param loginOnly  {@code true} when the phone belongs to a provisioned
 *                   {@code COGNILOGISTIC_ADMIN} — signup and PIN reset are not
 *                   available; use {@code POST /auth/login} only.
 */
public record CheckPhoneResponse(boolean registered, boolean loginOnly) {}
