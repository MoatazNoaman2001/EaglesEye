package io.eagleseye.pipeline.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eagleseye.pipeline.telemetry.Telemetry;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * The enricher (T-501): canonical telemetry in, vehicle-aware domain events out.
 *
 *  - resolves IMEI -> vehicle/tenant ({@link DeviceDirectory})
 *  - derives operational status (FR-TRK-03)
 *  - maintains live state in Valkey (the live map reads THIS, never the database)
 *  - publishes to Valkey pub/sub for WebSocket fan-out (T-503)
 *  - emits the enriched event on `events.domain`, keyed by vehicle (AO-03)
 */
@ApplicationScoped
public class Enricher {

    private static final Logger LOG = Logger.getLogger(Enricher.class);

    /** TODO(T-210/T-411): read from platform settings; fixed for now. */
    private static final double MOVING_SPEED_KMH = 5.0;
    private static final Duration LIVE_STATE_TTL = Duration.ofMinutes(10);
    private static final String PUBSUB_CHANNEL = "positions";

    @Inject ObjectMapper mapper;
    @Inject DeviceDirectory directory;
    @Inject RedisDataSource redis;

    private HashCommands<String, String, String> liveState;
    private PubSubCommands<String> pubsub;

    @PostConstruct
    void init() {
        liveState = redis.hash(String.class);
        pubsub = redis.pubsub(String.class);
    }

    public record EnrichedPosition(
            String eventType,       // "position" — events.domain later carries more types
            String vehicleId,
            String tenantId,
            VehicleStatus status,
            Telemetry telemetry) {}

    @Incoming("telemetry-decoded-in")
    @Outgoing("events-domain")
    public Record<String, String> enrich(String json) {
        try {
            Telemetry t = mapper.readValue(json, Telemetry.class);

            var binding = directory.resolve(t.imei()).orElse(null);
            if (binding == null) {
                // unknown device: telemetry from unregistered IMEIs is dropped here,
                // logged for the health/rejection view (FR-ING-06)
                LOG.warnf("No vehicle binding for IMEI %s — dropping", t.imei());
                return null;
            }

            VehicleStatus status = deriveStatus(t);
            EnrichedPosition event = new EnrichedPosition("position", binding.vehicleId(), binding.tenantId(), status, t);
            String eventJson = mapper.writeValueAsString(event);

            updateLiveState(event);
            pubsub.publish(PUBSUB_CHANNEL, eventJson);

            return Record.of(binding.vehicleId(), eventJson);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to enrich, dropping: %.200s", json);
            return null;
        }
    }

    private static VehicleStatus deriveStatus(Telemetry t) {
        if (!t.fixValid()) return VehicleStatus.NO_FIX;
        if (t.speedKmh() != null && t.speedKmh() > MOVING_SPEED_KMH) return VehicleStatus.MOVING;
        if (Boolean.TRUE.equals(t.ignition())) return VehicleStatus.IDLING;
        return VehicleStatus.STOPPED;
        // OFFLINE is a staleness state — the health watchdog owns it (T-801), not per-message logic
    }

    private void updateLiveState(EnrichedPosition e) {
        Telemetry t = e.telemetry();
        Map<String, String> state = new HashMap<>();
        state.put("vehicleId", e.vehicleId());
        state.put("tenantId", e.tenantId());
        state.put("imei", t.imei());
        state.put("status", e.status().name());
        state.put("lat", String.valueOf(t.latitude()));
        state.put("lon", String.valueOf(t.longitude()));
        if (t.speedKmh() != null) state.put("speedKmh", String.format("%.1f", t.speedKmh()));
        if (t.headingDeg() != null) state.put("headingDeg", String.valueOf(t.headingDeg()));
        state.put("deviceTime", String.valueOf(t.deviceTime()));
        state.put("protocol", t.protocol());

        String key = "vehicle:" + e.vehicleId();
        liveState.hset(key, state);
        redis.key().expire(key, LIVE_STATE_TTL);
    }
}
