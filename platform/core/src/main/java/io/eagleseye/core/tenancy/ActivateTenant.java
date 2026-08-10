package io.eagleseye.core.tenancy;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource whose database work must run under the current tenant's
 * row-level security context. Pair with class-level @Transactional so the
 * SET LOCAL lives inside the same transaction as the queries.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ActivateTenant {
}
