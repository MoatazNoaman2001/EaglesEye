package io.eagleseye.core.digest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Daily Digest (T-711) — the product's heartbeat per the thesis:
 * one verdict message per tenant per morning. Exceptions first, framed in money
 * (T-713), reassurance when nothing happened. Arabic-first, WhatsApp-ready
 * plain text (no markup, no emoji) — the transport swaps later (T-714).
 */
@ApplicationScoped
public class DigestService {

    private static final Logger LOG = Logger.getLogger(DigestService.class);
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");   // tenant tz setting later (FR-LOC-03)

    @Inject @DataSource("system") AgroalDataSource dataSource;   // cross-tenant by design (owner role)
    @Inject ObjectMapper mapper;
    @Inject Mailer mailer;

    @ConfigProperty(name = "eagleseye.digest.email.to")
    Optional<String> emailTo;

    private record VehicleDay(String label, int trips, double km, double maxSpeed) {}

    /** 06:00 every morning — digest for yesterday, every known tenant. */
    @Scheduled(cron = "0 0 6 * * ?")
    void nightly() {
        LocalDate yesterday = LocalDate.now(CAIRO).minusDays(1);
        for (String tenant : tenants()) {
            try {
                generate(tenant, yesterday);
            } catch (Exception e) {
                LOG.errorf(e, "Digest generation failed for tenant %s", tenant);
            }
        }
    }

    public Map<String, String> generate(String tenantId, LocalDate date) throws Exception {
        Instant from = date.atStartOfDay(CAIRO).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(CAIRO).toInstant();

        try (Connection c = dataSource.getConnection()) {
            Map<String, VehicleDay> perVehicle = new LinkedHashMap<>();
            double totalKm = 0;
            int totalTrips = 0;
            long totalIdleSeconds = 0;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT t.vehicle_id, COALESCE(NULLIF(v.name,''), v.plate, t.vehicle_id) AS label,
                           count(*), COALESCE(sum(t.distance_km),0), COALESCE(max(t.max_speed_kmh),0),
                           COALESCE(sum(t.idle_seconds),0)
                    FROM trips t
                    LEFT JOIN vehicles v ON v.id::text = t.vehicle_id
                    WHERE t.tenant_id = ? AND t.start_time >= ? AND t.start_time < ?
                    GROUP BY t.vehicle_id, label ORDER BY 4 DESC
                    """)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        VehicleDay day = new VehicleDay(rs.getString(2), rs.getInt(3), rs.getDouble(4), rs.getDouble(5));
                        perVehicle.put(rs.getString(1), day);
                        totalKm += day.km();
                        totalTrips += day.trips();
                        totalIdleSeconds += rs.getLong(6);
                    }
                }
            }

            List<String[]> alerts = new ArrayList<>();   // [severity, message]
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT severity, message FROM alerts
                    WHERE tenant_id = ? AND time >= ? AND time < ? ORDER BY time
                    """)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) alerts.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }

            int reported = scalar(c,
                    "SELECT count(DISTINCT device_imei) FROM positions WHERE tenant_id=? AND device_time>=? AND device_time<?",
                    tenantId, from, to);
            int registered = scalar(c,
                    "SELECT count(*) FROM devices WHERE tenant_id=? AND status='REGISTERED'", tenantId, null, null);

            double fuelPrice = settingDouble(c, "money.fuel.price.per.liter", 15.0);
            double per100 = settingDouble(c, "money.fuel.consumption.l.per.100km", 12.0);
            double idleBurn = settingDouble(c, "money.idle.burn.l.per.hour", 2.5);
            double fuelCost = totalKm * per100 / 100.0 * fuelPrice;
            double idleCost = totalIdleSeconds / 3600.0 * idleBurn * fuelPrice;
            long idleMin = totalIdleSeconds / 60;

            List<VehicleDay> vehicles = List.copyOf(perVehicle.values());
            String ar = composeAr(date, vehicles, totalTrips, totalKm, fuelCost, alerts, reported, registered)
                    + (idleMin > 0 ? String.format(Locale.US, "تكدس بلا حركة: %d دقيقة ≈ %.0f جنيه\n", idleMin, idleCost) : "");
            String en = composeEn(date, vehicles, totalTrips, totalKm, fuelCost, alerts, reported, registered)
                    + (idleMin > 0 ? String.format(Locale.US, "Idle: %d min ≈ %.0f EGP wasted\n", idleMin, idleCost) : "");

            ObjectNode stats = mapper.createObjectNode();
            stats.put("totalKm", round1(totalKm));
            stats.put("totalTrips", totalTrips);
            stats.put("alerts", alerts.size());
            stats.put("fuelCostEstimate", round1(fuelCost));
            stats.put("idleSeconds", totalIdleSeconds);
            stats.put("idleCostEstimate", round1(idleCost));
            stats.put("devicesReported", reported);
            stats.put("devicesRegistered", registered);

            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO digests (tenant_id, digest_date, text_ar, text_en, stats)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, digest_date)
                    DO UPDATE SET text_ar = EXCLUDED.text_ar, text_en = EXCLUDED.text_en,
                                  stats = EXCLUDED.stats, created_at = now()
                    """)) {
                ps.setString(1, tenantId);
                ps.setObject(2, date);
                ps.setString(3, ar);
                ps.setString(4, en);
                ps.setString(5, stats.toString());
                ps.executeUpdate();
            }

            emailTo.ifPresent(address -> {
                try {
                    mailer.send(Mail.withText(address, "EaglesEye — " + date + " digest", ar + "\n\n----\n\n" + en));
                } catch (Exception e) {
                    LOG.warnf(e, "Digest email failed (stored digest unaffected)");
                }
            });

            LOG.infof("Digest %s/%s: %d trips, %.1f km, %d alert(s)", tenantId, date, totalTrips, totalKm, alerts.size());
            return Map.of("ar", ar, "en", en, "stats", stats.toString());
        }
    }

    private String composeAr(LocalDate date, List<VehicleDay> vehicles, int trips, double km,
                             double fuelCost, List<String[]> alerts, int reported, int registered) {
        StringBuilder s = new StringBuilder();
        s.append("تقرير EaglesEye — ").append(date).append('\n');
        if (trips == 0 && alerts.isEmpty()) {
            s.append("لا رحلات ولا تنبيهات في هذا اليوم.\n");
        } else {
            s.append(String.format(Locale.US, "الرحلات: %d رحلة، %.1f كم إجمالاً\n", trips, km));
            s.append(String.format(Locale.US, "تقدير الوقود: حوالي %.0f جنيه\n", fuelCost));
            s.append('\n');
            if (alerts.isEmpty()) {
                s.append("التنبيهات: لا شيء — يوم هادئ.\n");
            } else {
                s.append("التنبيهات (").append(alerts.size()).append("):\n");
                for (String[] a : alerts) {
                    s.append("- [").append(sevAr(a[0])).append("] ").append(a[1]).append('\n');
                }
            }
            s.append('\n');
            for (VehicleDay v : vehicles) {
                s.append(String.format(Locale.US, "%s: %d رحلة، %.1f كم، أقصى سرعة %.0f كم/س\n",
                        v.label(), v.trips(), v.km(), v.maxSpeed()));
            }
        }
        s.append('\n');
        s.append(reported >= registered && registered > 0
                ? "الأجهزة: كلها تعمل بشكل طبيعي (" + reported + "/" + registered + ")\n"
                : "الأجهزة: " + reported + " من " + registered + " أرسلت بيانات\n");
        return s.toString();
    }

    private String composeEn(LocalDate date, List<VehicleDay> vehicles, int trips, double km,
                             double fuelCost, List<String[]> alerts, int reported, int registered) {
        StringBuilder s = new StringBuilder();
        s.append("EaglesEye report — ").append(date).append('\n');
        if (trips == 0 && alerts.isEmpty()) {
            s.append("No trips and no alerts on this day.\n");
        } else {
            s.append(String.format(Locale.US, "Trips: %d, %.1f km total\n", trips, km));
            s.append(String.format(Locale.US, "Fuel estimate: ~%.0f EGP\n", fuelCost));
            s.append('\n');
            if (alerts.isEmpty()) {
                s.append("Alerts: none — a quiet day.\n");
            } else {
                s.append("Alerts (").append(alerts.size()).append("):\n");
                for (String[] a : alerts) {
                    s.append("- [").append(a[0]).append("] ").append(a[1]).append('\n');
                }
            }
            s.append('\n');
            for (VehicleDay v : vehicles) {
                s.append(String.format(Locale.US, "%s: %d trip(s), %.1f km, max %.0f km/h\n",
                        v.label(), v.trips(), v.km(), v.maxSpeed()));
            }
        }
        s.append('\n');
        s.append(reported >= registered && registered > 0
                ? "Devices: all healthy (" + reported + "/" + registered + ")\n"
                : "Devices: " + reported + " of " + registered + " reported\n");
        return s.toString();
    }

    private static String sevAr(String severity) {
        return switch (severity) {
            case "critical" -> "حرج";
            case "warning" -> "تحذير";
            default -> "معلومة";
        };
    }

    private List<String> tenants() {
        List<String> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM tenants");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        } catch (Exception e) {
            LOG.warnf(e, "Tenant listing failed");
        }
        return result;
    }

    private static int scalar(Connection c, String sql, String tenant, Instant from, Instant to) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenant);
            if (from != null) {
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static double settingDouble(Connection c, String key, double fallback) {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM platform_settings WHERE key = ?")) {
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
