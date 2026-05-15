package com.cognilogistic.notificationclient.model;

/**
 * Mobile platform of a registered push device.
 *
 * <p>Drives the platform-specific payload Azure Notification Hubs uses (APNS for iOS,
 * FCM for Android). Stored as the enum's {@code name()} string in the
 * {@code device_registrations.platform} VARCHAR(10) column.
 *
 * <p>Web / desktop push is not supported in the pilot. If it lands later this enum
 * needs a {@code WEB} value.
 *
 * <p><strong>Schema reference:</strong> {@code database/schema.sql} v5.0 lines 673–684
 * (column {@code device_registrations.platform}).
 */
public enum DevicePlatform {

    /** Apple iOS (iPhone / iPad). Azure NH dispatches via APNS. */
    IOS,

    /** Google Android. Azure NH dispatches via FCM. */
    ANDROID
}
