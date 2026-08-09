-- Vehicles, devices, and assignment history (FR-DEV-01..06).
-- tenant_id is a plain column until the tenant table lands (T-401); every row is
-- tenant-scoped from day one (AO-05) so RLS can be switched on without rewrites.

CREATE TABLE vehicles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT NOT NULL DEFAULT 'dev-tenant',
    plate       TEXT NOT NULL,
    name        TEXT,
    make        TEXT,
    model       TEXT,
    model_year  INT,
    category    TEXT,                          -- car / truck / bus / equipment ...
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, plate)
);

CREATE TABLE devices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT NOT NULL DEFAULT 'dev-tenant',
    imei          TEXT NOT NULL UNIQUE,        -- FR-ING-05: identity on the wire
    protocol      TEXT,                        -- teltonika / gt06 / osmand / mqtt-json
    model         TEXT,
    sim_msisdn    TEXT,
    status        TEXT NOT NULL DEFAULT 'REGISTERED',   -- REGISTERED | DECOMMISSIONED
    vehicle_id    UUID REFERENCES vehicles (id),         -- current assignment (null = spare)
    traccar_id    INT,                         -- bridge id (ADR-9 registry sync)
    notes         TEXT,                        -- installer, installation notes (FR-DEV-07)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decommissioned_at TIMESTAMPTZ
);

-- who was mounted where, when — billing/reporting correctness over time (FR-DEV-02)
CREATE TABLE device_assignments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id     UUID NOT NULL REFERENCES devices (id),
    vehicle_id    UUID NOT NULL REFERENCES vehicles (id),
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ
);

CREATE INDEX idx_devices_tenant ON devices (tenant_id);
CREATE INDEX idx_vehicles_tenant ON vehicles (tenant_id);
CREATE INDEX idx_assignments_device ON device_assignments (device_id, assigned_at DESC);
