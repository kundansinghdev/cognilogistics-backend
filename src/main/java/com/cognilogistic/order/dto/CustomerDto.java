package com.cognilogistic.order.dto;

/**
 * Read-model DTO returned by the customer lookup endpoint.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code id} — database primary key of the customer</li>
 *   <li>{@code whatsappPhone} — the unique WhatsApp phone used as the customer identifier</li>
 *   <li>{@code name} — customer display name (null for shadow customers — BR-04)</li>
 *   <li>{@code shadow} — {@code true} if this is an auto-created placeholder; the customer
 *       has not yet self-registered through the portal</li>
 * </ul>
 */
public record CustomerDto(String id, String whatsappPhone, String name, boolean shadow) {}
