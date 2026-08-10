package io.eagleseye.core.tenancy;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Resolves the tenant for every API request (T-401/T-403).
 *
 * Source of truth: the OIDC token's `tenant_id` claim — signed by Keycloak,
 * set at user creation, not spoofable. The X-Tenant-Id header is honoured
 * ONLY in dev mode and ONLY when the token carries no tenant (testing).
 * Either way, database RLS is the enforcement; this filter selects, never widens.
 */
@Provider
public class TenantFilter implements ContainerRequestFilter {

    @Inject
    TenantContext tenantContext;

    @Inject
    SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String claim = null;
        if (!identity.isAnonymous() && identity.getPrincipal() instanceof JsonWebToken token) {
            claim = token.getClaim("tenant_id");
        }
        if (claim != null && !claim.isBlank()) {
            tenantContext.set(claim);
            return;
        }
        if (LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            String header = ctx.getHeaderString("X-Tenant-Id");
            if (header != null && !header.isBlank()) {
                tenantContext.set(header.trim());
            }
        }
    }
}
