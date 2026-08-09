-- Alert rules and alert history (T-701/T-706 foundation, FR-ALT-01/02/07).
-- Rules are data: tenant-scoped, typed, parameterised via JSON — the console edits
-- them, the pipeline's RulesEngine evaluates them, no deploy needed for new instances.
CREATE TABLE alert_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT NOT NULL DEFAULT 'dev-tenant',
    name        TEXT NOT NULL,
    type        TEXT NOT NULL,     -- speeding | geofence_entry | geofence_exit | idle | after_hours | low_battery
    severity    TEXT NOT NULL DEFAULT 'warning',   -- info | warning | critical
    enabled     BOOLEAN NOT NULL DEFAULT true,
    params      TEXT NOT NULL DEFAULT '{}',        -- JSON: thresholds, zone filters, windows, cooldownSeconds
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alert_rules_tenant ON alert_rules (tenant_id);

CREATE TABLE alerts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT NOT NULL,
    vehicle_id    TEXT NOT NULL,
    vehicle_label TEXT,
    rule_id       UUID,
    rule_name     TEXT,
    type          TEXT NOT NULL,
    severity      TEXT NOT NULL,
    message       TEXT NOT NULL,
    time          TIMESTAMPTZ NOT NULL,
    context       TEXT,                            -- JSON snapshot (speed, zone, position...)
    acknowledged  BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_alerts_tenant_time ON alerts (tenant_id, time DESC);

-- dev-tenant starter rules (consoles edit from here)
INSERT INTO alert_rules (tenant_id, name, type, severity, params) VALUES
  ('dev-tenant', 'Speeding over 90 km/h', 'speeding', 'warning',
   '{"maxSpeedKmh": 90, "cooldownSeconds": 300}'),
  ('dev-tenant', 'Restricted zone entry', 'geofence_entry', 'critical',
   '{"zoneCategory": "restricted", "cooldownSeconds": 300}'),
  ('dev-tenant', 'Prolonged idling', 'idle', 'info',
   '{"maxIdleMinutes": 10, "cooldownSeconds": 600}');
