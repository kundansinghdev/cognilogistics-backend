package com.cognilogistic.user.dto;

/**
 * Read-model DTO for a branch office returned by the API.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code id} — database primary key of the office</li>
 *   <li>{@code name} — display name of the branch (e.g. "Mumbai HQ")</li>
 *   <li>{@code city}, {@code state}, {@code pincode} — location of the branch</li>
 *   <li>{@code primary} — {@code true} for the designated primary/head office of the TP account;
 *       there is exactly one primary office per TP account</li>
 * </ul>
 */
public record OfficeDto(String id, String name, String city, String state, String pincode, boolean primary) {}
