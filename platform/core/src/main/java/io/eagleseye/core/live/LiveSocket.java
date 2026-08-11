package io.eagleseye.core.live;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

/**
 * Live push socket (T-503): the browser connects here and receives enriched
 * position events in real time instead of polling. On open it gets a snapshot of
 * the current live state; thereafter {@link LiveBroadcaster} pushes each update
 * as the enricher publishes it to Valkey pub/sub.
 *
 * TODO(T-403): scope the stream to the connection's tenant once auth lands; for
 * now the single dev tenant sees everything.
 */
@WebSocket(path = "/ws/live")
public class LiveSocket {

    @Inject
    LiveState liveState;

    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        // first frame: a full snapshot so a fresh client is immediately correct
        return liveState.snapshotJson();
    }
}
