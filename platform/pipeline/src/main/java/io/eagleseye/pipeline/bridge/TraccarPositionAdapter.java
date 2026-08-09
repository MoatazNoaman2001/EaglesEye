package io.eagleseye.pipeline.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eagleseye.pipeline.telemetry.Telemetry;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ADR-9 boundary: consumes Traccar's forwarded position envelopes and republishes
 * them as canonical {@link Telemetry} on `telemetry.decoded`, keyed by IMEI so
 * per-device ordering is preserved. Downstream of this class, Traccar does not exist.
 *
 * Traccar speed arrives in KNOTS (its internal unit) and is converted to km/h here —
 * the only place in the platform where that conversion is allowed to happen.
 */
@ApplicationScoped
public class TraccarPositionAdapter {

    private static final Logger LOG = Logger.getLogger(TraccarPositionAdapter.class);
    private static final double KNOTS_TO_KMH = 1.852;

    @Inject
    ObjectMapper mapper;

    @Incoming("traccar-positions")
    @Outgoing("telemetry-decoded")
    public Record<String, String> adapt(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode position = root.path("position");
            JsonNode device = root.path("device");

            String imei = device.path("uniqueId").asText(null);
            if (imei == null || position.isMissingNode()) {
                LOG.warnf("Dropping malformed bridge message (no device/position): %.200s", json);
                return null;
            }

            Map<String, Object> attributes = new HashMap<>();
            JsonNode attrs = position.path("attributes");
            attrs.properties().forEach(e -> attributes.put("traccar." + e.getKey(), toJavaValue(e.getValue())));
            attributes.put("bridge", "traccar");

            Boolean ignition = attrs.has("ignition") ? attrs.get("ignition").asBoolean() : null;

            Telemetry telemetry = new Telemetry(
                    imei,
                    position.path("protocol").asText("unknown"),
                    parseInstant(position.path("deviceTime").asText(null)),
                    parseInstant(position.path("serverTime").asText(null)),
                    position.path("latitude").asDouble(),
                    position.path("longitude").asDouble(),
                    position.hasNonNull("altitude") ? position.get("altitude").asDouble() : null,
                    position.hasNonNull("speed") ? position.get("speed").asDouble() * KNOTS_TO_KMH : null,
                    position.hasNonNull("course") ? (int) Math.round(position.get("course").asDouble()) : null,
                    position.path("valid").asBoolean(false),
                    ignition,
                    attributes);

            return Record.of(imei, mapper.writeValueAsString(telemetry));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to adapt bridge message, dropping: %.200s", json);
            return null;  // dropped; dead-letter topic arrives with T-301 reconciliation work
        }
    }

    private static Instant parseInstant(String iso) {
        return iso == null ? null : java.time.OffsetDateTime.parse(iso).toInstant();
    }

    private static Object toJavaValue(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isNumber()) return node.asDouble();
        return node.asText();
    }
}
