-- Tenancy backbone (T-401, FR-SEC-01/02/08, AO-01/02/05).
-- The API connects as eagleseye_app (non-owner, no BYPASSRLS): row-level security
-- decides what it sees, driven by app.tenant_id set per request. System components
-- (pipeline, digest, migrations) stay on the owner role and legitimately see all
-- tenants. This is isolation enforced by the database, not by application code.

CREATE TABLE tenants (
    id            TEXT PRIMARY KEY,                -- slug, e.g. 'dev-tenant'
    name          TEXT NOT NULL,
    parent_id     TEXT REFERENCES tenants (id),    -- reseller hierarchy (AO-01)
    logo_url      TEXT,                            -- branding now, UI later (AO-02)
    primary_color TEXT,
    timezone      TEXT NOT NULL DEFAULT 'Africa/Cairo',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO tenants (id, name) VALUES ('dev-tenant', 'Development Tenant');

-- application role (password is DEV ONLY — production rotates it via ops, never git)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'eagleseye_app') THEN
        CREATE ROLE eagleseye_app LOGIN PASSWORD 'eagleseye-app-dev';
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO eagleseye_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO eagleseye_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO eagleseye_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO eagleseye_app;

-- row-level security: policy applies to eagleseye_app; owner (system) is exempt
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['vehicles','devices','geofences','alert_rules','alerts','trips','digests']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation ON %I
            USING (tenant_id = current_setting('app.tenant_id', true))
            WITH CHECK (tenant_id = current_setting('app.tenant_id', true))
        $p$, t);
    END LOOP;
END $$;

-- positions: Timescale forbids RLS on compressed hypertables, so the app role is
-- barred from the table entirely and reads through a security-barrier view that
-- applies the same tenant predicate. Equivalent DB-level enforcement.
REVOKE ALL ON positions FROM eagleseye_app;
CREATE VIEW positions_tenant WITH (security_barrier) AS
    SELECT * FROM positions
    WHERE tenant_id = current_setting('app.tenant_id', true);
GRANT SELECT ON positions_tenant TO eagleseye_app;

-- device_assignments has no tenant column: scope through its device
ALTER TABLE device_assignments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON device_assignments
    USING (EXISTS (SELECT 1 FROM devices d
                   WHERE d.id = device_assignments.device_id
                     AND d.tenant_id = current_setting('app.tenant_id', true)));

-- platform_settings and tenants stay platform-scoped (no RLS): settings power
-- system features for all tenants; write-protection arrives with roles (T-403).
