/**
 * Notification client module — scaffold only for UAT.
 *
 * Post-UAT this owns: notification_preferences, notification_log, WhatsApp template generator (Hindi),
 * push fanout via Azure Notification Hubs, and a separate notification-worker Container App that
 * consumes order/tender/fleet events from Event Hubs.
 */
package com.cognilogistic.notificationclient;
