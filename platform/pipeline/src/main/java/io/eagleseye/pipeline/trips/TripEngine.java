package io.eagleseye.pipeline.trips;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streaming trip segmentation (T-601, first cut, FR-TRP-01/03):
 *
 *   MOVING and no open trip            -> trip opens at this position
 *   every position while open          -> distance accumulates (haversine), max speed tracked
 *   not MOVING for `close-after`       -> trip closes at the last moving position
 *
 * Deliberately simple v1: distance is the raw GPS chain (OSRM matching refines it in
 * T-602); late/buffered data triggers recompute in T-603; per-tenant thresholds come
 * from the settings service (FR-TRP-08). Raw positions stay the source of truth —
 * trips can always be rebuilt.
 */
@ApplicationScoped
public class TripEngine {

    private static final Logger LOG = Logger.getLogger(TripEngine.class);

    @ConfigProperty(name = "eagleseye.trips.close-after-seconds", defaultValue = "300")
    long closeAfterSeconds;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    private static final class OpenTrip {
        UUID id;
        double lastLat, lastLon;
        Instant lastMovingTime;
        double distanceKm;
        double maxSpeedKmh;
        int positions;
    }

    private final Map<String, OpenTrip> openTrips = new ConcurrentHashMap<>();

    @Incoming("trips-in")
    public void onEvent(String json) {
        try {
            JsonNode e = mapper.readTree(json);
            if (!"position".equals(e.path("eventType").asText())) return;
            JsonNode t = e.path("telemetry");

            String vehicleId = e.path("vehicleId").asText();
            String status = e.path("status").asText();
            double lat = t.path("latitude").asDouble();
            double lon = t.path("longitude").asDouble();
            double speed = t.hasNonNull("speedKmh") ? t.get("speedKmh").asDouble() : 0;
            Instant time = Instant.parse(t.path("deviceTime").asText());

            OpenTrip trip = openTrips.get(vehicleId);

            if ("MOVING".equals(status)) {
                if (trip == null) {
                    trip = open(e, t, lat, lon, time);
                    openTrips.put(vehicleId, trip);
                } else {
                    trip.distanceKm += haversineKm(trip.lastLat, trip.lastLon, lat, lon);
                    trip.maxSpeedKmh = Math.max(trip.maxSpeedKmh, speed);
                    trip.positions++;
                    trip.lastLat = lat;
                    trip.lastLon = lon;
                    trip.lastMovingTime = time;
                    update(trip);
                }
            } else if (trip != null
                    && Duration.between(trip.lastMovingTime, time).getSeconds() >= closeAfterSeconds) {
                close(trip);
                openTrips.remove(vehicleId);
                LOG.infof("Trip closed for %s: %.2f km, max %.0f km/h, %d positions",
                        vehicleId, trip.distanceKm, trip.maxSpeedKmh, trip.positions);
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Trip engine failed on event, skipping: %.200s", json);
        }
    }

    private OpenTrip open(JsonNode e, JsonNode t, double lat, double lon, Instant time) throws Exception {
        OpenTrip trip = new OpenTrip();
        trip.id = UUID.randomUUID();
        trip.lastLat = lat;
        trip.lastLon = lon;
        trip.lastMovingTime = time;
        trip.positions = 1;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO trips (id, tenant_id, vehicle_id, device_imei, start_time,
                                        start_lat, start_lon, position_count)
                     VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            ps.setObject(1, trip.id);
            ps.setString(2, e.path("tenantId").asText("dev-tenant"));
            ps.setString(3, e.path("vehicleId").asText());
            ps.setString(4, t.path("imei").asText());
            ps.setTimestamp(5, Timestamp.from(time));
            ps.setDouble(6, lat);
            ps.setDouble(7, lon);
            ps.executeUpdate();
        }
        LOG.infof("Trip opened for %s at (%.4f, %.4f)", e.path("vehicleId").asText(), lat, lon);
        return trip;
    }

    private void update(OpenTrip trip) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE trips SET distance_km = ?, max_speed_kmh = ?, position_count = ? WHERE id = ?")) {
            ps.setFloat(1, (float) trip.distanceKm);
            ps.setFloat(2, (float) trip.maxSpeedKmh);
            ps.setInt(3, trip.positions);
            ps.setObject(4, trip.id);
            ps.executeUpdate();
        }
    }

    private void close(OpenTrip trip) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE trips SET end_time = ?, end_lat = ?, end_lon = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.from(trip.lastMovingTime));
            ps.setDouble(2, trip.lastLat);
            ps.setDouble(3, trip.lastLon);
            ps.setObject(4, trip.id);
            ps.executeUpdate();
        }
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
