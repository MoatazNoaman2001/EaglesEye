package io.eagleseye.core.fleet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Publishes the current device→vehicle binding to the compacted `registry.devices`
 * topic (key = IMEI) whenever a device changes. The pipeline's DeviceDirectory
 * consumes this to resolve telemetry without ever calling core (AO-03: the bus is
 * the integration point, not RPC).
 *
 * On startup every device is republished — compaction keeps the topic tidy and a
 * fresh consumer always converges to the full registry.
 */
@ApplicationScoped
public class RegistryPublisher {

    private static final Logger LOG = Logger.getLogger(RegistryPublisher.class);

    @Inject
    @Channel("registry-devices")
    Emitter<Record<String, String>> emitter;

    @Inject
    ObjectMapper mapper;

    public void publish(Device device) {
        try {
            Vehicle vehicle = device.vehicleId != null ? Vehicle.findById(device.vehicleId) : null;
            ObjectNode node = mapper.createObjectNode();
            node.put("imei", device.imei);
            node.put("tenantId", device.tenantId);
            node.put("status", device.status);
            node.put("vehicleId", device.vehicleId != null ? device.vehicleId.toString() : null);
            node.put("vehicleLabel", vehicle != null
                    ? (vehicle.name != null && !vehicle.name.isBlank() ? vehicle.name : vehicle.plate)
                    : null);
            emitter.send(Record.of(device.imei, node.toString()));
        } catch (Exception e) {
            // registry is eventually consistent: startup republish + compaction heal gaps
            LOG.warnf(e, "Failed to publish registry binding for %s", device.imei);
        }
    }

    /** Republish the whole registry on boot so a fresh compacted topic converges. */
    void onStart(@Observes StartupEvent event) {
        Device.<Device>listAll().forEach(this::publish);
        LOG.infof("Registry republished: %d device(s)", Device.count());
    }
}
