package io.eagleseye.pipeline.telemetry;

import java.time.Instant;
import java.util.Map;

/**
 * The canonical telemetry message on the `telemetry.decoded` topic — EaglesEye's
 * own dialect. Everything downstream (enricher, trips, geofences, rules, health)
 * consumes THIS shape and never knows which bridge or decoder produced it (ADR-9).
 *
 * Units are fixed here once, for everyone: km/h, metres, degrees, UTC instants.
 *
 * @param imei        device identifier as reported by the protocol
 * @param protocol    source protocol name (e.g. "osmand", "gt06", "teltonika")
 * @param deviceTime  timestamp the device reported for this fix (UTC)
 * @param serverTime  when the bridge received it (UTC) — the live-edge/backlog discriminator
 * @param latitude    WGS-84 degrees
 * @param longitude   WGS-84 degrees
 * @param altitudeM   metres, or null when not reported
 * @param speedKmh    km/h (converted from Traccar's knots), or null
 * @param headingDeg  course over ground 0-359, or null
 * @param fixValid    device reports a valid GPS fix
 * @param ignition    ignition state, or null when the frame doesn't carry it
 * @param attributes  protocol/bridge extras passed through under stable keys
 */
public record Telemetry(
        String imei,
        String protocol,
        Instant deviceTime,
        Instant serverTime,
        double latitude,
        double longitude,
        Double altitudeM,
        Double speedKmh,
        Integer headingDeg,
        boolean fixValid,
        Boolean ignition,
        Map<String, Object> attributes) {
}
