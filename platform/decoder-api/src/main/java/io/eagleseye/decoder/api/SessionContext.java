package io.eagleseye.decoder.api;

import java.net.SocketAddress;
import java.util.Optional;

/**
 * Per-connection state handed to a decoder on every call.
 *
 * <p>The gateway owns the lifecycle: it creates one context per device connection
 * (or per datagram source for UDP) and authenticates the IMEI against the device
 * registry (FR-ING-05). Decoders read identity from here and may stash small
 * protocol state (e.g. a login sequence number) between frames.</p>
 */
public interface SessionContext {

    /** The device identifier (IMEI) once the protocol's login/identification frame has been seen. */
    Optional<String> imei();

    /**
     * Called by the decoder when the protocol's identification frame arrives.
     * The gateway authenticates it; returns {@code false} if the IMEI is not registered,
     * in which case the decoder should stop decoding for this session (FR-ING-06).
     */
    boolean identify(String imei);

    /** Remote address of the device connection, for diagnostics and logging. */
    SocketAddress remoteAddress();

    /** Protocol-scoped session attribute storage (login state, codec variant, …). */
    <T> Optional<T> attribute(String key, Class<T> type);

    void setAttribute(String key, Object value);
}
