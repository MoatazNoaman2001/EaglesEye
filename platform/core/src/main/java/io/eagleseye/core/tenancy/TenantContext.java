package io.eagleseye.core.tenancy;

import jakarta.enterprise.context.RequestScoped;

/**
 * The current request's tenant. Set by {@link TenantFilter} — interim from the
 * X-Tenant-Id header; from the OIDC token's tenant claim once Keycloak lands (T-403).
 */
@RequestScoped
public class TenantContext {

    public static final String DEFAULT_TENANT = "dev-tenant";

    private String tenantId = DEFAULT_TENANT;

    public String tenantId() {
        return tenantId;
    }

    void set(String tenantId) {
        this.tenantId = tenantId;
    }
}
