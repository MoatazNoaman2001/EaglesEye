-- Daily Digest (T-711/T-713): one verdict message per tenant per day.
-- Both languages stored; stats JSON keeps the raw numbers for later analytics.
CREATE TABLE digests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT NOT NULL,
    digest_date DATE NOT NULL,
    text_ar     TEXT NOT NULL,
    text_en     TEXT NOT NULL,
    stats       TEXT NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, digest_date)
);

-- money model (T-713): the numbers that turn telemetry into pounds
INSERT INTO platform_settings (key, value, value_type, description) VALUES
  ('money.fuel.price.per.liter', '15.0', 'string',
   'Fuel price per liter (EGP) used for cost estimates in digests and reports.'),
  ('money.fuel.consumption.l.per.100km', '12.0', 'string',
   'Average fleet fuel consumption (liters per 100 km) for cost estimates. Tenant-tunable.')
ON CONFLICT (key) DO NOTHING;
