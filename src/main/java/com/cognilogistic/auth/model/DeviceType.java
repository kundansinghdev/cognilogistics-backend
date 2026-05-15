package com.cognilogistic.auth.model;

/**
 * Indicates the type of client device that initiated an authentication request.
 *
 * <p>The device type influences the JWT access-token TTL:
 * <ul>
 *   <li>{@link #WEB} — shorter TTL (configured via {@code auth.jwt.webAccessTtlMinutes})</li>
 *   <li>{@link #MOBILE} — longer TTL (configured via {@code auth.jwt.mobileAccessTtlMinutes}),
 *       because mobile apps typically keep the user logged in for longer periods</li>
 * </ul>
 */
public enum DeviceType {
    /** Browser-based client (React app, etc.). */
    WEB,
    /** Android or iOS native application. */
    MOBILE
}
