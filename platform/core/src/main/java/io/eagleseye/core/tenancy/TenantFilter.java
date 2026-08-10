package io.eagleseye.core.tenancy;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolves the tenant for every API request.
 *
 * INTERIM (until T-403): the X-Tenant-Id header, defaulting to dev-tenant.
 * With Keycloak, this filter reads the token's tenant claim instead and the
 * header stops being trusted. Either way, the database RLS is the enforcement —
 * this filter only *selects* the tenant, it cannot widen access.
 */
@Provider
public class TenantFilter implements ContainerRequestFilter {

    @Inject
    TenantContext tenantContext;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String header = ctx.getHeaderString("X-Tenant-Id");
        if (header != null && !header.isBlank()) {
            tenantContext.set(header.trim());
        }
    }
}
