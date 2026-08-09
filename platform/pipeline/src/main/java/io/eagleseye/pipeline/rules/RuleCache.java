package io.eagleseye.pipeline.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

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
 * Enabled alert rules per tenant, refreshed lazily every 60 s (same pattern as
 * ZoneCache). Rules edited in the console become live within a minute.
 */
@ApplicationScoped
public class RuleCache {

    private static final Logger LOG = Logger.getLogger(RuleCache.class);
    private static final Duration REFRESH_EVERY = Duration.ofSeconds(60);

    public record Rule(UUID id, String tenantId, String name, String type,
                       String severity, JsonNode params, long cooldownSeconds) {}

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper mapper;

    private volatile Map<String, List<Rule>> rulesByTenant = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public List<Rule> rulesFor(String tenantId) {
        if (Duration.between(loadedAt, Instant.now()).compareTo(REFRESH_EVERY) > 0) {
            refresh();
        }
        return rulesByTenant.getOrDefault(tenantId, List.of());
    }

    private synchronized void refresh() {
        if (Duration.between(loadedAt, Instant.now()).compareTo(REFRESH_EVERY) <= 0) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, tenant_id, name, type, severity, params FROM alert_rules WHERE enabled");
             ResultSet rs = ps.executeQuery()) {
            Map<String, List<Rule>> fresh = new HashMap<>();
            int count = 0;
            while (rs.next()) {
                try {
                    JsonNode params = mapper.readTree(rs.getString(6));
                    long cooldown = params.path("cooldownSeconds").asLong(600);   // FR-ALT-06: always some cooldown
                    fresh.computeIfAbsent(rs.getString(2), k -> new ArrayList<>())
                            .add(new Rule((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                                    rs.getString(4), rs.getString(5), params, cooldown));
                    count++;
                } catch (Exception e) {
                    LOG.warnf("Skipping unparsable rule %s: %s", rs.getObject(1), e.getMessage());
                }
            }
            rulesByTenant = fresh;
            loadedAt = Instant.now();
            LOG.debugf("Rule cache refreshed: %d rule(s)", count);
        } catch (Exception e) {
            LOG.warnf(e, "Rule cache refresh failed — keeping previous rules");
            loadedAt = Instant.now();
        }
    }
}
