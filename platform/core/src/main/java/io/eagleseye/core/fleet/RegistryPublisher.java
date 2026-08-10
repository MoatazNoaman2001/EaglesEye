package io.eagleseye.core.fleet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
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

    @Inject
    @DataSource("system")
    AgroalDataSource systemDs;   // startup republish is cross-tenant by design

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
        int count = 0;
        try (var c = systemDs.getConnection();
             var ps = c.prepareStatement("""
                     SELECT d.imei, d.tenant_id, d.status, d.vehicle_id::text,
                            COALESCE(NULLIF(v.name,''), v.plate)
                     FROM devices d LEFT JOIN vehicles v ON v.id = d.vehicle_id
                     """);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("imei", rs.getString(1));
                node.put("tenantId", rs.getString(2));
                node.put("status", rs.getString(3));
                node.put("vehicleId", rs.getString(4));
                node.put("vehicleLabel", rs.getString(5));
                emitter.send(Record.of(rs.getString(1), node.toString()));
                count++;
            }
        } catch (Exception e) {
            LOG.warnf(e, "Startup registry republish failed (compaction heals on next boot)");
        }
        LOG.infof("Registry republished: %d device(s)", count);
    }
}
