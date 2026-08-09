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
 * v1 honesty: single-fix transitions (two-fix hysteresis and time-window rules
 * follow with the rules engine); in-memory presence state (recompute T-603 will
 * make it rebuildable from the topic).
 */
@ApplicationScoped
public class GeofenceEvaluator {

    private static final Logger LOG = Logger.getLogger(GeofenceEvaluator.class);
    private static final GeometryFactory GEOMETRY = new GeometryFactory();

    @Inject
    ZoneCache zones;

    @Inject
    ObjectMapper mapper;

    @Inject
    @Channel("events-domain")
    Emitter<Record<String, String>> events;

    /** vehicleId|zoneId -> entry time; presence = key exists */
    private final Map<String, Instant> presence = new ConcurrentHashMap<>();

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
                Instant enteredAt = presence.get(key);

                if (inside && enteredAt == null) {
                    presence.put(key, time);
                    publish(vehicleId, event(e, zone, "geofence.entered", time, null));
                    LOG.infof("%s ENTERED %s", vehicleId, zone.name());
                } else if (!inside && enteredAt != null) {
                    presence.remove(key);
                    long dwell = Duration.between(enteredAt, time).getSeconds();
                    publish(vehicleId, event(e, zone, "geofence.exited", time, dwell));
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
