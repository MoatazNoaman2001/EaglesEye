package io.eagleseye.pipeline.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agroal.api.AgroalDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules engine (T-701, FR-ALT-01/02) with MANDATORY cooldown dedup
 * (T-703, FR-ALT-06 — the anti-noise law: an alert that fires twice in its
 * cooldown window simply does not exist).
 *
 * Consumes everything on events.domain: position events feed speeding / idle /
 * after-hours / low-battery rules; geofence events feed entry/exit rules.
 * Fired alerts go to `alerts.outbound` (keyed by tenant) for delivery (T-704/705)
 * and into the alerts table for history (FR-ALT-07).
 */
@ApplicationScoped
public class RulesEngine {

    private static final Logger LOG = Logger.getLogger(RulesEngine.class);

    @Inject RuleCache rules;
    @Inject ObjectMapper mapper;
    @Inject RedisDataSource redis;
    @Inject AgroalDataSource dataSource;

    @Inject
    @Channel("alerts-out")
    Emitter<Record<String, String>> alerts;

    private ValueCommands<String, String> values;

    /** vehicleId -> when IDLING started (for the idle-duration rule). */
    private final Map<String, Instant> idlingSince = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        values = redis.value(String.class);
    }

    @Incoming("rules-in")
    public void onEvent(String json) {
        try {
            JsonNode e = mapper.readTree(json);
            String eventType = e.path("eventType").asText();
            switch (eventType) {
                case "position" -> onPosition(e);
                case "geofence.entered" -> onGeofence(e, "geofence_entry");
                case "geofence.exited" -> onGeofence(e, "geofence_exit");
                default -> { /* other event types are not rule inputs (yet) */ }
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Rule evaluation failed, skipping: %.200s", json);
        }
    }

    private void onPosition(JsonNode e) {
        String tenantId = e.path("tenantId").asText("dev-tenant");
        String vehicleId = e.path("vehicleId").asText();
        JsonNode t = e.path("telemetry");
        String status = e.path("status").asText();
        Instant time = Instant.parse(t.path("deviceTime").asText());
        Double speed = t.hasNonNull("speedKmh") ? t.get("speedKmh").asDouble() : null;

        trackIdling(vehicleId, status, time);

        for (RuleCache.Rule rule : rules.rulesFor(tenantId)) {
            switch (rule.type()) {
                case "speeding" -> {
                    double max = rule.params().path("maxSpeedKmh").asDouble(120);
                    if (speed != null && speed > max) {
                        fire(rule, e, time, String.format("%s at %.0f km/h (limit %.0f)",
                                label(e), speed, max), Map.of("speedKmh", speed, "maxSpeedKmh", max));
                    }
                }
                case "after_hours" -> {
                    if ("MOVING".equals(status) && inWindow(rule.params(), time)) {
                        fire(rule, e, time, label(e) + " moving outside allowed hours",
                                Map.of("speedKmh", speed == null ? 0 : speed));
                    }
                }
                case "low_battery" -> {
                    double threshold = rule.params().path("minPercent").asDouble(20);
                    JsonNode batt = t.path("attributes").path("phone.batteryPct");
                    if (batt.isNumber() && batt.asDouble() < threshold) {
                        fire(rule, e, time, String.format("%s battery at %d%%", label(e), batt.asInt()),
                                Map.of("batteryPct", batt.asInt()));
                    }
                }
                case "idle" -> {
                    Instant since = idlingSince.get(vehicleId);
                    long maxMinutes = rule.params().path("maxIdleMinutes").asLong(15);
                    if (since != null && Duration.between(since, time).toMinutes() >= maxMinutes) {
                        fire(rule, e, time, String.format("%s idling for %d min",
                                label(e), Duration.between(since, time).toMinutes()),
                                Map.of("idleMinutes", Duration.between(since, time).toMinutes()));
                    }
                }
                default -> { /* geofence rules handled on geofence events */ }
            }
        }
    }

    private void onGeofence(JsonNode e, String ruleType) {
        String tenantId = e.path("tenantId").asText("dev-tenant");
        Instant time = Instant.parse(e.path("time").asText());
        for (RuleCache.Rule rule : rules.rulesFor(tenantId)) {
            if (!rule.type().equals(ruleType)) continue;
            String wantedCategory = rule.params().path("zoneCategory").asText(null);
            if (wantedCategory != null && !wantedCategory.equals(e.path("zoneCategory").asText())) continue;
            JsonNode zoneIds = rule.params().path("zoneIds");
            if (zoneIds.isArray() && !containsText(zoneIds, e.path("zoneId").asText())) continue;

            String verb = "geofence_entry".equals(ruleType) ? "entered" : "left";
            fire(rule, e, time, String.format("%s %s %s", label(e), verb, e.path("zoneName").asText()),
                    Map.of("zoneId", e.path("zoneId").asText(), "zoneName", e.path("zoneName").asText(),
                           "dwellSeconds", e.path("dwellSeconds").asLong(0)));
        }
    }

    /** FR-ALT-06 — the cooldown gate. No alert escapes it. */
    private void fire(RuleCache.Rule rule, JsonNode event, Instant time, String message, Map<String, Object> context) {
        String vehicleId = event.path("vehicleId").asText();
        String cooldownKey = "cooldown:" + rule.id() + ":" + vehicleId;
        if (!values.setnx(cooldownKey, "1")) return;   // suppressed — inside cooldown window
        redis.key().expire(cooldownKey, Duration.ofSeconds(rule.cooldownSeconds()));

        try {
            UUID alertId = UUID.randomUUID();
            ObjectNode alert = mapper.createObjectNode();
            alert.put("alertId", alertId.toString());
            alert.put("tenantId", rule.tenantId());
            alert.put("vehicleId", vehicleId);
            alert.put("vehicleLabel", label(event));
            alert.put("ruleId", rule.id().toString());
            alert.put("ruleName", rule.name());
            alert.put("type", rule.type());
            alert.put("severity", rule.severity());
            alert.put("message", message);
            alert.put("time", time.toString());
            alert.set("context", mapper.valueToTree(context));

            alerts.send(Record.of(rule.tenantId(), alert.toString()));
            persist(alertId, rule, event, time, message, alert.get("context").toString());
            LOG.infof("ALERT [%s] %s", rule.severity(), message);
        } catch (Exception e) {
            LOG.errorf(e, "Alert publication failed for rule %s", rule.name());
        }
    }

    private void persist(UUID id, RuleCache.Rule rule, JsonNode event, Instant time,
                         String message, String context) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO alerts (id, tenant_id, vehicle_id, vehicle_label, rule_id, rule_name,
                                         type, severity, message, time, context)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setObject(1, id);
            ps.setString(2, rule.tenantId());
            ps.setString(3, event.path("vehicleId").asText());
            ps.setString(4, label(event));
            ps.setObject(5, rule.id());
            ps.setString(6, rule.name());
            ps.setString(7, rule.type());
            ps.setString(8, rule.severity());
            ps.setString(9, message);
            ps.setTimestamp(10, Timestamp.from(time));
            ps.setString(11, context);
            ps.executeUpdate();
        }
    }

    private void trackIdling(String vehicleId, String status, Instant time) {
        if ("IDLING".equals(status)) idlingSince.putIfAbsent(vehicleId, time);
        else idlingSince.remove(vehicleId);
    }

    /** Overnight-safe time window in the rule's timezone (FR-LOC-03 default Cairo). */
    private static boolean inWindow(JsonNode params, Instant time) {
        try {
            ZoneId zone = ZoneId.of(params.path("tz").asText("Africa/Cairo"));
            LocalTime now = LocalTime.ofInstant(time, zone);
            LocalTime start = LocalTime.parse(params.path("start").asText("22:00"));
            LocalTime end = LocalTime.parse(params.path("end").asText("06:00"));
            return start.isBefore(end)
                    ? !now.isBefore(start) && now.isBefore(end)
                    : !now.isBefore(start) || now.isBefore(end);   // overnight window
        } catch (Exception e) {
            return false;
        }
    }

    private static String label(JsonNode event) {
        String label = event.path("vehicleLabel").asText(null);
        return label != null && !label.isBlank() ? label : event.path("vehicleId").asText();
    }

    private static boolean containsText(JsonNode array, String value) {
        for (JsonNode n : array) if (value.equals(n.asText())) return true;
        return false;
    }
}
