package io.eagleseye.core.trips;

import io.agroal.api.AgroalDataSource;
import io.eagleseye.core.tenancy.ActivateTenant;
import io.eagleseye.core.tenancy.TenantContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily summary per vehicle (FR-TRP-06): distance, driving time, idle time, stop
 * count — the operational digest of a day, and the source of the money framing
 * (idle hours x burn rate x fuel price).
 */
@Path("/api/v1/summary")
@Produces(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class SummaryResource {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");   // tenant tz later (FR-LOC-03)

    public record DailySummary(String vehicleId, String vehicleLabel, String date,
                               double distanceKm, long drivingSeconds, long idleSeconds,
                               int trips, int stops, double maxSpeedKmh,
                               double fuelCostEgp, double idleCostEgp) {}

    @Inject
    AgroalDataSource dataSource;

    @Inject
    TenantContext tenant;

    @GET
    public List<DailySummary> daily(@QueryParam("date") String date) {
        LocalDate day = date != null && !date.isBlank()
                ? LocalDate.parse(date) : LocalDate.now(CAIRO).minusDays(1);
        Instant from = day.atStartOfDay(CAIRO).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(CAIRO).toInstant();

        double fuelPrice = setting("money.fuel.price.per.liter", 15.0);
        double per100 = setting("money.fuel.consumption.l.per.100km", 12.0);
        double idleBurn = setting("money.idle.burn.l.per.hour", 2.5);

        List<DailySummary> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT t.vehicle_id,
                            COALESCE(NULLIF(v.name,''), v.plate, t.vehicle_id) AS label,
                            COALESCE(sum(t.distance_km),0), COALESCE(sum(t.driving_seconds),0),
                            COALESCE(sum(t.idle_seconds),0), count(*), COALESCE(max(t.max_speed_kmh),0),
                            (SELECT count(*) FROM stops s
                             WHERE s.vehicle_id = t.vehicle_id AND s.arrival >= ? AND s.arrival < ?)
                     FROM trips t
                     LEFT JOIN vehicles v ON v.id::text = t.vehicle_id
                     WHERE t.start_time >= ? AND t.start_time < ?
                     GROUP BY t.vehicle_id, label
                     ORDER BY 3 DESC
                     """)) {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
            ps.setTimestamp(3, Timestamp.from(from));
            ps.setTimestamp(4, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double km = rs.getDouble(3);
                    long idleSec = rs.getLong(5);
                    double fuelCost = km * per100 / 100.0 * fuelPrice;
                    double idleCost = idleSec / 3600.0 * idleBurn * fuelPrice;
                    result.add(new DailySummary(
                            rs.getString(1), rs.getString(2), day.toString(),
                            round1(km), rs.getLong(4), idleSec,
                            rs.getInt(6), rs.getInt(8), rs.getDouble(7),
                            round1(fuelCost), round1(idleCost)));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("summary query failed: " + e.getMessage(), e);
        }
        return result;
    }

    private double setting(String key, double fallback) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM platform_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Double.parseDouble(rs.getString(1)) : fallback;
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
