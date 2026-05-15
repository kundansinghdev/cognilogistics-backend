package com.cognilogistic.auth.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-parameter annotation that injects the currently authenticated
 * {@link AuthPrincipal} into a controller method parameter.
 *
 * <p>This is a meta-annotation wrapping Spring Security's {@code @AuthenticationPrincipal},
 * providing a shorter, domain-specific name. Usage:
 * <pre>{@code
 * public ApiResponse<OrderDto> create(@CurrentUser AuthPrincipal me, ...) { ... }
 * }</pre>
 *
 * <p>The parameter will be {@code null} if the endpoint is publicly accessible and the
 * request does not include a valid JWT.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {
}
