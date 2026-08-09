package io.eagleseye.decoder.api;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPipeline;

/**
 * The contract every protocol plugin implements. One implementation per protocol
 * family (teltonika-codec8, gt06, …), discovered via {@link java.util.ServiceLoader}.
 *
 * <p>A decoder's whole world is: bytes in, normalised {@link TelemetryRecord}s out,
 * protocol-correct ACK back. It knows nothing about tenants, vehicles, Kafka or
 * databases — that isolation is architectural law (AO-04, NFR-13) and is enforced
 * by build rules, not convention.</p>
 *
 * <p>Threading: the gateway guarantees a decoder instance is called from a single
 * thread per connection; implementations must not share mutable state across
 * sessions except via {@link SessionContext} attributes.</p>
 */
public interface ProtocolDecoder {

    /** Stable identifier, e.g. {@code "teltonika-codec8"}. Used in config, metrics and logs. */
    String protocolId();

    /**
     * Contribute framing handlers (length-prefix, delimiter…) for this protocol to the
     * Netty pipeline, so {@link #decode} always receives exactly one complete frame.
     */
    void configureFraming(ChannelPipeline pipeline);

    /**
     * Decode one complete frame. Called after framing; never with partial data.
     * Must be side-effect free apart from {@link SessionContext} attributes —
     * persistence and publishing are the gateway's job.
     */
    DecodeResult decode(ByteBuf frame, SessionContext ctx);

    /**
     * Build the acknowledgement the device expects for {@code result} (FR-ING-11).
     * The gateway sends it ONLY after durable persistence (NFR-04). Return {@code null}
     * when the protocol expects no response for this frame type.
     */
    ByteBuf buildAck(DecodeResult result, SessionContext ctx);
}
