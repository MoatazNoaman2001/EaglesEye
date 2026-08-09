package io.eagleseye.pipeline.geofence;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.io.WKTReader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory zones for the evaluator: geofences loaded from PostGIS as WKT and
 * prepared as JTS geometries (point-in-polygon in microseconds, no DB on the
 * hot path). Refreshes lazily at most once per minute — new zones become
 * active within 60 s, which matches FR-GEO expectations at pilot scale.
 */
@ApplicationScoped
public class ZoneCache {

    private static final Logger LOG = Logger.getLogger(ZoneCache.class);
    private static final Duration REFRESH_EVERY = Duration.ofSeconds(60);

    public record Zone(UUID id, String name, String category, PreparedGeometry geometry) {}

    @Inject
    AgroalDataSource dataSource;

    private volatile Map<String, List<Zone>> zonesByTenant = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public List<Zone> zonesFor(String tenantId) {
        if (Duration.between(loadedAt, Instant.now()).compareTo(REFRESH_EVERY) > 0) {
            refresh();
        }
        return zonesByTenant.getOrDefault(tenantId, List.of());
    }

    private synchronized void refresh() {
        if (Duration.between(loadedAt, Instant.now()).compareTo(REFRESH_EVERY) <= 0) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tenant_id, name, category, ST_AsText(geom) FROM geofences");
             ResultSet rs = ps.executeQuery()) {
            WKTReader reader = new WKTReader();
            Map<String, List<Zone>> fresh = new HashMap<>();
            int count = 0;
            while (rs.next()) {
                try {
                    PreparedGeometry geom = PreparedGeometryFactory.prepare(reader.read(rs.getString(5)));
                    fresh.computeIfAbsent(rs.getString(2), k -> new ArrayList<>())
                            .add(new Zone((UUID) rs.getObject(1), rs.getString(3), rs.getString(4), geom));
                    count++;
                } catch (Exception e) {
                    LOG.warnf("Skipping unparsable geofence %s: %s", rs.getObject(1), e.getMessage());
                }
            }
            zonesByTenant = fresh;
            loadedAt = Instant.now();
            LOG.debugf("Zone cache refreshed: %d zone(s), %d tenant(s)", count, fresh.size());
        } catch (Exception e) {
            LOG.warnf(e, "Zone cache refresh failed — keeping previous zones");
            loadedAt = Instant.now();   // back off a full interval before retrying
        }
    }
}
