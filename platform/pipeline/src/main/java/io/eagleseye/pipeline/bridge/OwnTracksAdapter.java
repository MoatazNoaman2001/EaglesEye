package io.eagleseye.pipeline.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eagleseye.pipeline.telemetry.Telemetry;
import io.smallrye.reactive.messaging.kafka.Record;
import io.smallrye.reactive.messaging.mqtt.ReceivingMqttMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The generic MQTT/JSON ingestion lane (FR-ING-04, T-303) — first concrete dialect:
 * OwnTracks. Phones publish location JSON to `owntracks/<user>/<device>`; this
 * adapter normalises it onto `telemetry.decoded`, where it becomes indistinguishable
 * from any GPS tracker. Device identity = the <device> segment of the MQTT topic.
 *
 * OwnTracks payload reference (booklet): _type=location, lat, lon, tst (epoch s),
 * vel (km/h), cog (course), alt (m), acc (m), batt (%).
 */
@ApplicationScoped
public class OwnTracksAdapter {

    private static final Logger LOG = Logger.getLogger(OwnTracksAdapter.class);

    @Inject
    ObjectMapper mapper;

    @Incoming("owntracks")
    @Outgoing("telemetry-decoded")
    public Message<Record<String, String>> adapt(Message<byte[]> in) {
        String topic = in instanceof ReceivingMqttMessage mqtt ? mqtt.getTopic() : "owntracks/unknown/unknown";
        try {
            JsonNode node = mapper.readTree(in.getPayload());
            if (!"location".equals(node.path("_type").asText())) {
                in.ack();   // status/lwt/waypoint messages — not positions
                return null;
            }
            String[] segments = topic.split("/");
            String deviceId = segments.length >= 3 ? segments[2] : segments[segments.length - 1];

            Map<String, Object> attributes = new HashMap<>();
            if (node.hasNonNull("acc")) attributes.put("gps.accuracyM", node.get("acc").asDouble());
            if (node.hasNonNull("batt")) attributes.put("phone.batteryPct", node.get("batt").asInt());
            attributes.put("bridge", "owntracks-mqtt");
            attributes.put("mqtt.topic", topic);

            Telemetry telemetry = new Telemetry(
                    deviceId,
                    "owntracks",
                    Instant.ofEpochSecond(node.path("tst").asLong(Instant.now().getEpochSecond())),
                    Instant.now(),
                    node.path("lat").asDouble(),
                    node.path("lon").asDouble(),
                    node.hasNonNull("alt") ? node.get("alt").asDouble() : null,
                    node.hasNonNull("vel") ? node.get("vel").asDouble() : null,   // already km/h
                    node.hasNonNull("cog") ? node.get("cog").asInt() : null,
                    true,
                    null,
                    attributes);

            return Message.of(Record.of(deviceId, mapper.writeValueAsString(telemetry)), () -> in.ack());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to adapt OwnTracks message on %s", topic);
            in.ack();
            return null;
        }
    }
}
