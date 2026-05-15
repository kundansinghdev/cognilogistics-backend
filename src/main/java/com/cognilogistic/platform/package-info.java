/**
 * Platform module — cross-cutting infrastructure used by every other module.
 *
 * <p>Owns: {@link com.cognilogistic.platform.BaseEntity} (mapped-superclass
 * with {@code created_at} / {@code updated_at} populated by Spring Data JPA
 * auditing), and the {@code platform.api} sub-package
 * (response envelope, error codes, exception classes, global exception handler).
 *
 * <p>Depends on: nothing in this codebase — it is the foundation layer.
 * Depended on by: every other module. All persistent entities should extend
 * {@code BaseEntity}; every REST endpoint must return
 * {@link com.cognilogistic.platform.api.ApiResponse} and signal failures by
 * throwing {@link com.cognilogistic.platform.api.ApiException}.
 *
 * <p>Read first: {@link com.cognilogistic.platform.api.ApiResponse} for the
 * single response envelope shape, then
 * {@link com.cognilogistic.platform.api.ErrorCode} for the closed set of error
 * codes the API can emit.
 */
package com.cognilogistic.platform;
