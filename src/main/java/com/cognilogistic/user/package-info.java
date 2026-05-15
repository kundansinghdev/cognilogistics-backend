/**
 * User module — TP account, branch offices, user-office assignments.
 *
 * <p>Owns: tp_accounts, offices, user_office_assignments. Encodes the
 * "primary vs. branch" privilege split: TP_ADMIN users have full access to
 * the TP account; TP_BRANCH users are limited to the offices they are mapped to.
 *
 * <p>Depends on: {@code auth} (for the {@link com.cognilogistic.auth.security.AuthPrincipal}
 * that carries the role/tp_account_id) and {@code platform}.
 * Depended on by: {@code order} (every transition that touches an office checks
 * membership through {@code OfficeRepository} / {@code UserOfficeAssignmentRepository}).
 *
 * <p>Read first: {@link com.cognilogistic.user.model.Office} for the office entity
 * shape, and {@link com.cognilogistic.user.service.OfficeService} for the
 * branch-management endpoints used during onboarding.
 */
package com.cognilogistic.user;
