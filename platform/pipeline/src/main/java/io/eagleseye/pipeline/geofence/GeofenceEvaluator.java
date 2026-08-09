package io.eagleseye.pipeline.geofence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geofence evaluator (T-606, FR-GEO-04/05): consumes enriched positions, detects
 * zone entry/exit per vehicle, and publishes geofence events back onto
 * `events.domain` — where the rules engine, digest and reports consume them (AO-03).
 *
 * Boundary flapping (GPS jitter dancing across a zone edge) is suppressed by
 * two-fix hysteresis: a transition fires only after `hysteresis-fixes` consecutive
 * fixes agree on the new side, and the event carries the time of the FIRST such
 * fix — so dwell stays accurate. In-memory state (recompute T-603 will make it
 * rebuildable from the topic); time-window rules land with the rules engine.
 */
@ApplicationScoped
public class GeofenceEvaluator {

    private static final Logger LOG = Logger.getLogger(GeofenceEvaluator.class);
    private static final GeometryFactory GEOMETRY = new GeometryFactory();

    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "eagleseye.geofence.hysteresis-fixes", defaultValue = "2")
    int hysteresisFixes;

    @Inject
    ZoneCache zones;

    @Inject
    ObjectMapper mapper;

    @Inject
    @Channel("events-domain")
    Emitter<Record<String, String>> events;

    /** Presence state per vehicle|zone, with a pending side awaiting confirmation. */
    private static final class ZoneState {
        boolean inside;            // confirmed side
        Instant enteredAt;         // when confirmed inside (event-accurate)
        int pendingCount;          // consecutive fixes on the other side
        Instant pendingSince;      // first fix of the pending run
    }

    private final Map<String, ZoneState> states = new ConcurrentHashMap<>();

    @Incoming("geofence-in")
    public void evaluate(String json) {
        try {
            JsonNode e = mapper.readTree(json);
            if (!"position".equals(e.path("eventType").asText())) return;   // ignore our own output

            String vehicleId = e.path("vehicleId").asText();
            String tenantId = e.path("tenantId").asText("dev-tenant");
            JsonNode t = e.path("telemetry");
            Instant time = Instant.parse(t.path("deviceTime").asText());
            Point point = GEOMETRY.createPoint(
                    new Coordinate(t.path("longitude").asDouble(), t.path("latitude").asDouble()));

            for (ZoneCache.Zone zone : zones.zonesFor(tenantId)) {
                String key = vehicleId + "|" + zone.id();
                boolean inside = zone.geometry().contains(point);
                ZoneState s = states.computeIfAbsent(key, k -> new ZoneState());

                if (inside == s.inside) {
                    // fix agrees with the confirmed side — any pending flap dissolves
                    s.pendingCount = 0;
                    s.pendingSince = null;
                    continue;
                }
                if (s.pendingCount == 0) s.pendingSince = time;
                s.pendingCount++;
                if (s.pendingCount < hysteresisFixes) continue;   // not confirmed yet

                // confirmed transition, effective at the first pending fix
                Instant at = s.pendingSince;
                s.inside = inside;
                s.pendingCount = 0;
                s.pendingSince = null;
                if (inside) {
                    s.enteredAt = at;
                    publish(vehicleId, event(e, zone, "geofence.entered", at, null));
                    LOG.infof("%s ENTERED %s", vehicleId, zone.name());
                } else {
                    long dwell = s.enteredAt != null ? Duration.between(s.enteredAt, at).getSeconds() : 0;
                    s.enteredAt = null;
                    publish(vehicleId, event(e, zone, "geofence.exited", at, dwell));
                    LOG.infof("%s EXITED %s after %ds", vehicleId, zone.name(), dwell);
                }
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Geofence evaluation failed, skipping: %.200s", json);
        }
    }

    private ObjectNode event(JsonNode position, ZoneCache.Zone zone, String type, Instant at, Long dwellSeconds) {
        ObjectNode n = mapper.createObjectNode();
        n.put("eventType", type);
        n.put("vehicleId", position.path("vehicleId").asText());
        n.put("vehicleLabel", position.path("vehicleLabel").asText(null));
        n.put("tenantId", position.path("tenantId").asText("dev-tenant"));
        n.put("zoneId", zone.id().toString());
        n.put("zoneName", zone.name());
        n.put("zoneCategory", zone.category());
        n.put("time", at.toString());
        if (dwellSeconds != null) n.put("dwellSeconds", dwellSeconds);
        return n;
    }

    private void publish(String vehicleId, ObjectNode event) {
        events.send(Record.of(vehicleId, event.toString()));
    }
}
