package io.eagleseye.decoder.api;

import java.time.Instant;
import java.util.Map;

/**
 * One normalised telemetry sample produced by a decoder, independent of the wire protocol.
 *
 * <p>Core positioning fields are first-class; everything protocol-specific (IO elements,
 * event codes, sensor values) goes into {@link #attributes} under stable string keys so
 * downstream modules never need protocol knowledge (BRD BR-02).</p>
 *
 * @param deviceTime  timestamp reported by the device (NOT arrival time — buffered
 *                    backlog records carry their original time, FR-ING-07)
 * @param latitude    WGS-84 degrees
 * @param longitude   WGS-84 degrees
 * @param altitudeM   metres above sea level, or {@code null} if not reported
 * @param speedKmh    speed in km/h, or {@code null}
 * @param headingDeg  course over ground 0-359, or {@code null}
 * @param satellites  visible satellite count, or {@code null}
 * @param fixValid    whether the device reports a valid GPS fix
 * @param ignition    ignition state, or {@code null} when the protocol/frame doesn't carry it
 * @param attributes  protocol-specific extras (voltages, IO elements, event codes…);
 *                    never {@code null}, may be empty
 */
public record TelemetryRecord(
        Instant deviceTime,
        double latitude,
        double longitude,
        Double altitudeM,
        Double speedKmh,
        Integer headingDeg,
        Integer satellites,
        boolean fixValid,
        Boolean ignition,
        Map<String, Object> attributes) {

    public TelemetryRecord {
        if (deviceTime == null) throw new IllegalArgumentException("deviceTime is required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Well-known attribute keys shared across protocols. Decoders should prefer these. */
    public static final class Keys {
        public static final String EXTERNAL_VOLTAGE_MV = "power.externalMv";
        public static final String BATTERY_VOLTAGE_MV = "power.batteryMv";
        public static final String GSM_SIGNAL = "gsm.signal";
        public static final String EVENT_CODE = "event.code";
        public static final String ODOMETER_M = "odometer.m";
        public static final String RAW_PRIORITY = "raw.priority";

        private Keys() {
        }
    }
}
