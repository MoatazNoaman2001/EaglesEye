package io.eagleseye.core.live;

import io.eagleseye.core.tenancy.TenantContext;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live vehicle state for the map (FR-TRK-01). Reads ONLY Valkey — the enricher keeps
 * it current; the database is never touched on this hot path (NFR-02).
 *
 * TODO(T-403/T-404): tenant + group scoping once auth exists. TODO(T-503): replace
 * polling with WebSocket push fed by the enricher's pub/sub channel.
 */
@Path("/api/v1/live")
@ApplicationScoped
public class LiveResource {

    @Inject
    RedisDataSource redis;

    @Inject
    TenantContext tenant;

    @GET
    @Path("/vehicles")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, String>> vehicles() {
        List<Map<String, String>> result = new ArrayList<>();
        var hash = redis.hash(String.class);
        var cursor = redis.key().scan(new KeyScanArgs().match("vehicle:*").count(200));
        while (cursor.hasNext()) {
            for (String key : cursor.next()) {
                Map<String, String> state = hash.hgetall(key);
                if (!state.isEmpty() && tenant.tenantId().equals(state.get("tenantId"))) result.add(state);
            }
        }
        return result;
    }
}
