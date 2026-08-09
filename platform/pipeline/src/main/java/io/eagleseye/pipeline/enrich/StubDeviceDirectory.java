package io.eagleseye.pipeline.enrich;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * TEMPORARY (until T-205/T-404): every IMEI maps to a same-named vehicle in the
 * single dev tenant. Lets the enrichment pipeline run end-to-end before the
 * registry exists. Replaced by a registry.devices-backed implementation.
 */
@ApplicationScoped
public class StubDeviceDirectory implements DeviceDirectory {

    @Override
    public Optional<Binding> resolve(String imei) {
        return Optional.of(new Binding("veh-" + imei, "dev-tenant"));
    }
}
