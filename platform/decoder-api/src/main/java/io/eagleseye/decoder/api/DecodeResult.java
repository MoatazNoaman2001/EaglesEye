package io.eagleseye.decoder.api;

import java.util.List;

/**
 * Outcome of decoding one framed message.
 *
 * <p>A single frame may carry zero records (login/heartbeat frames), one record,
 * or many (Teltonika Codec 8 batches, buffered backlog dumps — FR-ING-07).</p>
 *
 * <p>{@code recordCount} is what the protocol ACK must confirm. The gateway calls
 * {@link ProtocolDecoder#buildAck} only AFTER the records are durably persisted
 * (NFR-04): ack-after-persist is the platform's no-data-loss guarantee, and it is
 * enforced by the gateway, never by decoders.</p>
 */
public record DecodeResult(List<TelemetryRecord> records, boolean identified, int recordCount) {

    public DecodeResult {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** A frame that produced telemetry records. */
    public static DecodeResult of(List<TelemetryRecord> records) {
        return new DecodeResult(records, false, records == null ? 0 : records.size());
    }

    /** A login/identification frame: no records, session is now identified. */
    public static DecodeResult identifiedFrame() {
        return new DecodeResult(List.of(), true, 0);
    }

    /** A frame that needs no records and no identity change (heartbeat, status). */
    public static DecodeResult empty() {
        return new DecodeResult(List.of(), false, 0);
    }
}
