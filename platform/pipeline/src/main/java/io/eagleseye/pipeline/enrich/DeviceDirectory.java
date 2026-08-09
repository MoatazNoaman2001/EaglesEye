package io.eagleseye.pipeline.enrich;

import java.util.Optional;

/**
 * Resolves a device identifier to its vehicle and tenant.
 *
 * The real implementation consumes the compacted `registry.devices` topic fed by
 * core's device management (T-205 / T-404-405) and honours assignment history
 * (FR-DEV-02). Until that exists, {@link StubDeviceDirectory} stands in.
 */
public interface DeviceDirectory {

    record Binding(String vehicleId, String tenantId) {}

    Optional<Binding> resolve(String imei);
}
