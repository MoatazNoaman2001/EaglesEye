package io.eagleseye.core.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.runtime.Startup;
import io.quarkus.websockets.next.OpenConnections;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Bridges Valkey pub/sub to the WebSocket clients (T-503). The enricher publishes
 * each enriched position to the "positions" channel; this subscriber wraps it as
 * {type:"position", ...} and fans it out to every open {@link LiveSocket}.
 *
 * Reactive subscription (kept alive for the app lifetime) + non-blocking sends,
 * so nothing blocks the Redis event loop.
 */
@Startup
@ApplicationScoped
public class LiveBroadcaster {

    private static final Logger LOG = Logger.getLogger(LiveBroadcaster.class);

    @Inject ReactiveRedisDataSource reactiveRedis;
    @Inject ObjectMapper mapper;
    @Inject OpenConnections connections;

    private Cancellable subscription;

    @PostConstruct
    void start() {
        subscription = reactiveRedis.pubsub(String.class)
                .subscribe("positions")
                .subscribe().with(
                        this::broadcast,
                        err -> LOG.error("Live pub/sub subscription failed", err));
        LOG.info("Live broadcaster subscribed to Valkey 'positions'");
    }

    @PreDestroy
    void stop() {
        if (subscription != null) subscription.cancel();
    }

    private void broadcast(String enrichedEventJson) {
        try {
            JsonNode event = mapper.readTree(enrichedEventJson);
            var frame = mapper.createObjectNode();
            frame.put("type", "position");
            frame.set("event", event);
            String text = frame.toString();
            connections.forEach(c -> c.sendText(text).subscribe().with(x -> {}, e -> {}));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to broadcast live event");
        }
    }
}
