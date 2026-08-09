-- Runtime configuration: operational parameters are data, not code (Arch §7).
-- Platform scope only for now; tenant_settings overrides arrive with tenancy (T-401).
CREATE TABLE platform_settings (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    value_type  TEXT NOT NULL DEFAULT 'string',   -- string | int | bool
    description TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform_settings (key, value, value_type, description) VALUES
  ('data.retention.months',        '12',  'int',
   'How many months of position history stay online and queryable (NFR-06, OQ-07). Applied to the positions hypertable by the retention applier (T-207).'),
  ('timescale.compression.after.days', '7', 'int',
   'Positions chunks older than this many days are compressed (~10x smaller, faster per-vehicle reads).'),
  ('alerts.cooldown.default.seconds',  '600', 'int',
   'Default suppression window for repeating alerts (FR-ALT-06). Rules can override per rule.'),
  ('trips.idle.threshold.seconds',     '300', 'int',
   'Engine on + no movement for this long counts as idling (FR-TRP-08). Tenant-overridable later.');
