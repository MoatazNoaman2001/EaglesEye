-- Trips (T-601, first cut): streaming segmentation output. One row per trip;
-- end_time NULL = trip in progress. Map matching (T-602) and late-data recompute
-- (T-603) refine these rows later — raw positions remain the source of truth.
CREATE TABLE trips (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT NOT NULL,
    vehicle_id    TEXT NOT NULL,
    device_imei   TEXT NOT NULL,
    start_time    TIMESTAMPTZ NOT NULL,
    end_time      TIMESTAMPTZ,
    start_lat     DOUBLE PRECISION,
    start_lon     DOUBLE PRECISION,
    end_lat       DOUBLE PRECISION,
    end_lon       DOUBLE PRECISION,
    distance_km   REAL NOT NULL DEFAULT 0,     -- haversine chain for now; OSRM-matched later (T-602)
    max_speed_kmh REAL NOT NULL DEFAULT 0,
    position_count INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trips_vehicle_start ON trips (vehicle_id, start_time DESC);
CREATE INDEX idx_trips_tenant_start ON trips (tenant_id, start_time DESC);
