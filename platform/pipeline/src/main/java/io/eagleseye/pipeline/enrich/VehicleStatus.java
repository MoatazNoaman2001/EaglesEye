package io.eagleseye.pipeline.enrich;

/**
 * Per-vehicle operational status shown on the live map (FR-TRK-03).
 *
 * OFFLINE is deliberately absent: it is a state of *silence*, not of any message,
 * so it is derived by a staleness watchdog (device health work, T-801), never here.
 */
public enum VehicleStatus {
    MOVING,
    IDLING,
    STOPPED,
    NO_FIX
}
