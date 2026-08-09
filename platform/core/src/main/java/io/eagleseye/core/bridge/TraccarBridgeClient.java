package io.eagleseye.core.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Registry sync, core -> bridge (T-205 / ADR-9): mirrors device registration into
 * Traccar so the bridge accepts exactly the devices EaglesEye knows.
 *
 * Failures are logged, never fatal: the bridge is eventually consistent with our
 * registry; the reconciliation job (T-301) closes any gap. Our DB is the truth.
 */
@ApplicationScoped
public class TraccarBridgeClient {

    private static final Logger LOG = Logger.getLogger(TraccarBridgeClient.class);

    @ConfigProperty(name = "eagleseye.traccar.url", defaultValue = "http://localhost:8083")
    String baseUrl;

    @ConfigProperty(name = "eagleseye.traccar.token")
    Optional<String> token;

    @ConfigProperty(name = "eagleseye.traccar.sync-enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Creates the device in Traccar; returns its bridge id, or empty on failure/disabled. */
    public Optional<Integer> createDevice(String name, String imei) {
        if (!syncActive()) return Optional.empty();
        try {
            String body = mapper.writeValueAsString(
                    mapper.createObjectNode().put("name", name).put("uniqueId", imei));
            HttpRequest req = request("/api/devices")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                LOG.warnf("Traccar device create for %s -> HTTP %d: %.200s", imei, res.statusCode(), res.body());
                return Optional.empty();
            }
            JsonNode node = mapper.readTree(res.body());
            return Optional.of(node.get("id").asInt());
        } catch (Exception e) {
            LOG.warnf(e, "Traccar device create failed for %s (bridge will be reconciled, T-301)", imei);
            return Optional.empty();
        }
    }

    /** Removes the device from Traccar (used on decommission, FR-DEV-06). */
    public void deleteDevice(int traccarId) {
        if (!syncActive()) return;
        try {
            HttpRequest req = request("/api/devices/" + traccarId).DELETE().build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 2) {
                LOG.warnf("Traccar device delete %d -> HTTP %d", traccarId, res.statusCode());
            }
        } catch (Exception e) {
            LOG.warnf(e, "Traccar device delete failed for bridge id %d", traccarId);
        }
    }

    private boolean syncActive() {
        if (!enabled) return false;
        if (token.isEmpty()) {
            LOG.warn("eagleseye.traccar.token not set — bridge sync skipped");
            return false;
        }
        return true;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token.orElseThrow());
    }
}
