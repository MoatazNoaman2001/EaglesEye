-- Geofences (T-605, FR-GEO-01/02): circles and polygons, tenant-scoped, PostGIS-indexed.
-- Circles keep their definition (center+radius) for editing; both shapes are stored as
-- polygon geometry so evaluation and spatial queries treat everything uniformly.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE geofences (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT NOT NULL DEFAULT 'dev-tenant',
    name        TEXT NOT NULL,
    category    TEXT,                    -- customer_site | depot | restricted | ... (FR-GEO-02)
    color       TEXT,                    -- console display
    area_type   TEXT NOT NULL,           -- circle | polygon
    center_lat  DOUBLE PRECISION,        -- circle definition (null for polygons)
    center_lon  DOUBLE PRECISION,
    radius_m    REAL,
    geom        geometry(Polygon, 4326) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_geofences_geom ON geofences USING GIST (geom);
CREATE INDEX idx_geofences_tenant ON geofences (tenant_id);
