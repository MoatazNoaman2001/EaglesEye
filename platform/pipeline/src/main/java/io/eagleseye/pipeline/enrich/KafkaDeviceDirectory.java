package io.eagleseye.pipeline.enrich;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real device directory (completes T-501): an in-memory cache of the compacted
 * `registry.devices` topic, fed by core's RegistryPublisher. On startup the consumer
 * replays the compacted log from the beginning (unique consumer group per instance),
 * so every pipeline instance converges to the full registry within seconds.
 *
 * Unknown IMEIs: dropped by default (FR-ING-06). In dev, `allow-unknown` keeps
 * experiments (the phone, simulators) flowing with a synthetic binding.
 */
@ApplicationScoped
public class KafkaDeviceDirectory implements DeviceDirectory {

    private static final Logger LOG = Logger.getLogger(KafkaDeviceDirectory.class);
    private static final String DECOMMISSIONED = "DECOMMISSIONED";

    private record Entry(String status, Binding binding) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @ConfigProperty(name = "eagleseye.directory.allow-unknown", defaultValue = "false")
    boolean allowUnknown;

    @Inject
    ObjectMapper mapper;

    @Incoming("registry-devices")
    public void onBinding(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            String imei = n.path("imei").asText(null);
            if (imei == null) return;
            String status = n.path("status").asText("REGISTERED");
            String vehicleId = n.hasNonNull("vehicleId") ? n.get("vehicleId").asText() : "dev-" + imei;
            String label = n.hasNonNull("vehicleLabel") ? n.get("vehicleLabel").asText() : imei;
            String tenant = n.path("tenantId").asText("dev-tenant");
            entries.put(imei, new Entry(status, new Binding(vehicleId, tenant, label)));
            LOG.debugf("Registry updated: %s -> %s (%s)", imei, label, status);
        } catch (Exception e) {
            LOG.warnf(e, "Bad registry message ignored: %.200s", json);
        }
    }

    @Override
    public Optional<Binding> resolve(String imei) {
        Entry entry = entries.get(imei);
        if (entry != null) {
            if (DECOMMISSIONED.equals(entry.status())) {
                LOG.debugf("Telemetry from decommissioned device %s dropped", imei);
                return Optional.empty();
            }
            return Optional.of(entry.binding());
        }
        if (allowUnknown) {
            // dev convenience: phones and simulators flow without registration
            return Optional.of(new Binding("veh-" + imei, "dev-tenant", imei));
        }
        return Optional.empty();   // FR-ING-06: unregistered devices are rejected
    }
}
