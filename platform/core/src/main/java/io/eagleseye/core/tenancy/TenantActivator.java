package io.eagleseye.core.tenancy;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;

/**
 * Applies the request tenant to the database session: SET LOCAL app.tenant_id
 * inside the active transaction, which the RLS policies (V8) read via
 * current_setting. APPLICATION priority runs after the @Transactional
 * interceptor, so the setting binds to the right transaction and dies with it —
 * no tenant ever leaks across pooled connections.
 */
@ActivateTenant
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class TenantActivator {

    @Inject
    EntityManager em;

    @Inject
    TenantContext tenant;

    @AroundInvoke
    Object activate(InvocationContext ctx) throws Exception {
        em.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
                .setParameter(1, tenant.tenantId())
                .getSingleResult();
        return ctx.proceed();
    }
}
