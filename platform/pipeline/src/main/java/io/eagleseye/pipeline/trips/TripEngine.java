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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streaming trip segmentation with stops and idle (T-601, FR-TRP-01/03/04):
 *
 *   MOVING and no open trip     -> trip opens; if the vehicle was parked, the stop
 *                                  since the last trip is finalised (arrival->departure)
 *   position while trip open    -> distance accumulates (haversine); driving vs idle
 *                                  time split by speed (idle = near-zero within a trip)
 *   still for `close-after`     -> trip closes at the last moving position; a stop opens
 *
 * Industry definitions (researched): STOP = parked between trips (arrival/departure/
 * duration/location); IDLE = engine-on-stationary, proxied here by speed-near-zero
 * within a trip until real ignition data arrives. Distance is raw GPS (OSRM refines
 * in T-602); recompute is T-603. Raw positions stay the source of truth.
 */
@ApplicationScoped
public class TripEngine {

    private static final Logger LOG = Logger.getLogger(TripEngine.class);
    private static final double IDLE_SPEED_KMH = 3.0;   // below this, within a trip = idling

    @ConfigProperty(name = "eagleseye.trips.close-after-seconds", defaultValue = "300")
    long closeAfterSeconds;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    private static final class OpenTrip {
        UUID id;
        String tenantId;
        String imei;
        double lastLat, lastLon;
        Instant startTime;
        Instant lastTime;          // time of the previous position (for idle/drive split)
        Instant lastMovingTime;    // last time speed was above idle threshold
        double distanceKm;
        double maxSpeedKmh;
        long idleSeconds;
        long drivingSeconds;
        int positions;
    }

    /** Where each vehicle last parked, awaiting its next trip to become a completed stop. */
    private record PendingStop(Instant arrival, double lat, double lon) {}

    private final Map<String, OpenTrip> openTrips = new ConcurrentHashMap<>();
    private final Map<String, PendingStop> parked = new ConcurrentHashMap<>();

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
                    finalisePendingStop(vehicleId, time);          // vehicle leaves -> stop ends
                    trip = open(e, t, lat, lon, time);
                    openTrips.put(vehicleId, trip);
                } else {
                    accumulate(trip, lat, lon, speed, time);
                    update(trip);
                }
            } else if (trip != null) {
                // stationary while a trip is open: this is idle time (engine-on-stopped)
                accumulate(trip, lat, lon, speed, time);
                if (Duration.between(trip.lastMovingTime, time).getSeconds() >= closeAfterSeconds) {
                    close(trip);
                    openTrips.remove(vehicleId);
                    parked.put(vehicleId, new PendingStop(trip.lastMovingTime, trip.lastLat, trip.lastLon));
                    LOG.infof("Trip closed for %s: %.2f km, drive %ds, idle %ds, max %.0f km/h",
                            vehicleId, trip.distanceKm, trip.drivingSeconds, trip.idleSeconds, trip.maxSpeedKmh);
                } else {
                    update(trip);
                }
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Trip engine failed on event, skipping: %.200s", json);
        }
    }

    private void accumulate(OpenTrip trip, double lat, double lon, double speed, Instant time) {
        long gap = Math.max(0, Duration.between(trip.lastTime, time).getSeconds());
        if (speed < IDLE_SPEED_KMH) {
            trip.idleSeconds += gap;
        } else {
            trip.drivingSeconds += gap;
            trip.distanceKm += haversineKm(trip.lastLat, trip.lastLon, lat, lon);
            trip.maxSpeedKmh = Math.max(trip.maxSpeedKmh, speed);
            trip.lastMovingTime = time;
        }
        trip.lastLat = lat;
        trip.lastLon = lon;
        trip.lastTime = time;
        trip.positions++;
    }

    private OpenTrip open(JsonNode e, JsonNode t, double lat, double lon, Instant time) throws Exception {
        OpenTrip trip = new OpenTrip();
        trip.id = UUID.randomUUID();
        trip.tenantId = e.path("tenantId").asText("dev-tenant");
        trip.imei = t.path("imei").asText();
        trip.lastLat = lat;
        trip.lastLon = lon;
        trip.startTime = time;
        trip.lastTime = time;
        trip.lastMovingTime = time;
        trip.positions = 1;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO trips (id, tenant_id, vehicle_id, device_imei, start_time,
                                        start_lat, start_lon, position_count)
                     VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            ps.setObject(1, trip.id);
            ps.setString(2, trip.tenantId);
            ps.setString(3, e.path("vehicleId").asText());
            ps.setString(4, trip.imei);
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
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE trips SET distance_km = ?, max_speed_kmh = ?, position_count = ?,
                                      idle_seconds = ?, driving_seconds = ? WHERE id = ?
                     """)) {
            ps.setFloat(1, (float) trip.distanceKm);
            ps.setFloat(2, (float) trip.maxSpeedKmh);
            ps.setInt(3, trip.positions);
            ps.setInt(4, (int) trip.idleSeconds);
            ps.setInt(5, (int) trip.drivingSeconds);
            ps.setObject(6, trip.id);
            ps.executeUpdate();
        }
    }

    private void close(OpenTrip trip) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE trips SET end_time = ?, end_lat = ?, end_lon = ?,
                                      idle_seconds = ?, driving_seconds = ? WHERE id = ?
                     """)) {
            ps.setTimestamp(1, Timestamp.from(trip.lastMovingTime));
            ps.setDouble(2, trip.lastLat);
            ps.setDouble(3, trip.lastLon);
            ps.setInt(4, (int) trip.idleSeconds);
            ps.setInt(5, (int) trip.drivingSeconds);
            ps.setObject(6, trip.id);
            ps.executeUpdate();
        }
    }

    /** The vehicle just started moving: close the stop that ran since it last parked (FR-TRP-04). */
    private void finalisePendingStop(String vehicleId, Instant departure) throws Exception {
        PendingStop stop = parked.remove(vehicleId);
        if (stop == null) return;
        long duration = Math.max(0, Duration.between(stop.arrival(), departure).getSeconds());
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO stops (tenant_id, vehicle_id, device_imei, arrival, departure,
                                        duration_seconds, latitude, longitude)
                     SELECT tenant_id, vehicle_id, device_imei, ?, ?, ?, ?, ?
                     FROM trips WHERE vehicle_id = ? ORDER BY start_time DESC LIMIT 1
                     """)) {
            ps.setTimestamp(1, Timestamp.from(stop.arrival()));
            ps.setTimestamp(2, Timestamp.from(departure));
            ps.setInt(3, (int) duration);
            ps.setDouble(4, stop.lat());
            ps.setDouble(5, stop.lon());
            ps.setString(6, vehicleId);
            ps.executeUpdate();
        }
        LOG.infof("Stop recorded for %s: %d min parked", vehicleId, duration / 60);
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
