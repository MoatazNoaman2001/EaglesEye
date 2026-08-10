-- Stops, idle, and daily summaries (T-601 completion; FR-TRP-04/06).
--
-- Industry definitions (researched 10 Aug 2026):
--   STOP  = vehicle parked BETWEEN trips (inactive). Reported as arrival/departure/
--           duration/location — the gap from one trip's end to the next trip's start.
--   IDLE  = engine ON but stationary >= threshold (3-5 min standard). The fuel-waster.
--           Tracked as seconds; with only GPS (no ignition) we proxy it as speed-near-
--           zero time WITHIN a trip (traffic lights, loading) — ignition refines later.

-- idle accumulated during each trip (stationary-with-activity), in seconds
ALTER TABLE trips ADD COLUMN idle_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN driving_seconds INT NOT NULL DEFAULT 0;

-- stops between trips (FR-TRP-04)
CREATE TABLE stops (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT NOT NULL DEFAULT 'dev-tenant',
    vehicle_id  TEXT NOT NULL,
    device_imei TEXT NOT NULL,
    arrival     TIMESTAMPTZ NOT NULL,        -- previous trip's end
    departure   TIMESTAMPTZ,                 -- next trip's start (null = still parked)
    duration_seconds INT,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION
);
CREATE INDEX idx_stops_vehicle_arrival ON stops (vehicle_id, arrival DESC);
CREATE INDEX idx_stops_tenant_arrival ON stops (tenant_id, arrival DESC);

-- idle cost input (T-713 money model): litres/hour burned while idling
INSERT INTO platform_settings (key, value, value_type, description) VALUES
  ('money.idle.burn.l.per.hour', '2.5', 'string',
   'Fuel burned per hour while idling (litres). Idle cost = idle hours x this x fuel price.')
ON CONFLICT (key) DO NOTHING;

-- RLS: same policy shape as the other tenant tables (V8)
ALTER TABLE stops ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON stops
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON stops TO eagleseye_app;
