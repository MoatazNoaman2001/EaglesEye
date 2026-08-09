package io.eagleseye.pipeline.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * The timeseries writer (T-206): persists every enriched position into the
 * positions hypertable. `ON CONFLICT DO NOTHING` on (imei, device_time) makes
 * re-delivered frames harmless (FR-ING-08) — replays and retries are free.
 *
 * TODO(T-306): micro-batching before the 2,000 msg/s load test; row-at-a-time
 * is honest and correct at dev scale.
 */
@ApplicationScoped
public class PositionWriter {

    private static final Logger LOG = Logger.getLogger(PositionWriter.class);

    private static final String INSERT = """
            INSERT INTO positions (device_imei, vehicle_id, tenant_id, device_time, server_time,
                                   latitude, longitude, altitude_m, speed_kmh, heading_deg,
                                   fix_valid, ignition, status, attributes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (device_imei, device_time) DO NOTHING
            """;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    @Incoming("positions-store-in")
    public void store(String json) {
        try {
            JsonNode e = mapper.readTree(json);
            JsonNode t = e.path("telemetry");
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(INSERT)) {
                ps.setString(1, t.path("imei").asText());
                ps.setString(2, e.path("vehicleId").asText(null));
                ps.setString(3, e.path("tenantId").asText("dev-tenant"));
                ps.setTimestamp(4, ts(t.path("deviceTime").asText(null)));
                ps.setTimestamp(5, ts(t.path("serverTime").asText(null)));
                ps.setDouble(6, t.path("latitude").asDouble());
                ps.setDouble(7, t.path("longitude").asDouble());
                setNullable(ps, 8, t, "altitudeM");
                setNullable(ps, 9, t, "speedKmh");
                if (t.hasNonNull("headingDeg")) ps.setInt(10, t.get("headingDeg").asInt()); else ps.setNull(10, java.sql.Types.SMALLINT);
                ps.setBoolean(11, t.path("fixValid").asBoolean(true));
                if (t.hasNonNull("ignition")) ps.setBoolean(12, t.get("ignition").asBoolean()); else ps.setNull(12, java.sql.Types.BOOLEAN);
                ps.setString(13, e.path("status").asText(null));
                ps.setString(14, t.path("attributes").toString());
                ps.executeUpdate();
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Failed to store position, dropping: %.200s", json);
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, JsonNode t, String field) throws Exception {
        if (t.hasNonNull(field)) ps.setFloat(idx, (float) t.get(field).asDouble());
        else ps.setNull(idx, java.sql.Types.REAL);
    }

    private static Timestamp ts(String iso) {
        return iso == null ? null : Timestamp.from(OffsetDateTime.parse(iso).toInstant());
    }
}
