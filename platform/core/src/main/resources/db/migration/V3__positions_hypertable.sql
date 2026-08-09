-- The positions hypertable (T-207): every telemetry sample, 12 months online (NFR-06).
-- Written by the pipeline's position writer; read by history/replay and reports.
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE positions (
    device_imei  TEXT NOT NULL,
    vehicle_id   TEXT,
    tenant_id    TEXT NOT NULL,
    device_time  TIMESTAMPTZ NOT NULL,
    server_time  TIMESTAMPTZ,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    altitude_m   REAL,
    speed_kmh    REAL,
    heading_deg  SMALLINT,
    fix_valid    BOOLEAN NOT NULL DEFAULT true,
    ignition     BOOLEAN,
    status       TEXT,
    attributes   JSONB,
    -- the dedup contract (FR-ING-08): device + device_time is identity;
    -- re-delivered frames hit ON CONFLICT DO NOTHING at insert
    PRIMARY KEY (device_imei, device_time)
);

SELECT create_hypertable('positions', 'device_time');

-- per-vehicle history reads (NFR-07: 30 days for one vehicle < 3 s)
CREATE INDEX idx_positions_vehicle_time ON positions (vehicle_id, device_time DESC);

-- compression: same-device rows compress together (~10x), old chunks shrink after a week
ALTER TABLE positions SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'device_imei',
    timescaledb.compress_orderby   = 'device_time DESC'
);
SELECT add_compression_policy('positions', INTERVAL '7 days');

-- NFR-06 / OQ-07: months online, then dropped — the runtime setting
-- data.retention.months is applied to this policy by the retention applier (T-210)
SELECT add_retention_policy('positions', INTERVAL '12 months');
