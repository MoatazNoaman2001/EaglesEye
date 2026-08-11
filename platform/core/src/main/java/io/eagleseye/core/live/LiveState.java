package io.eagleseye.core.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads the current live vehicle state from Valkey (shared by the REST and WS paths). */
@ApplicationScoped
public class LiveState {

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper mapper;

    public List<Map<String, String>> snapshot() {
        List<Map<String, String>> result = new ArrayList<>();
        var hash = redis.hash(String.class);
        var cursor = redis.key().scan(new KeyScanArgs().match("vehicle:*").count(200));
        while (cursor.hasNext()) {
            for (String key : cursor.next()) {
                Map<String, String> state = hash.hgetall(key);
                if (!state.isEmpty()) result.add(state);
            }
        }
        return result;
    }

    /** Snapshot wrapped as {type:"snapshot", vehicles:[...]} for the WS client. */
    public String snapshotJson() {
        try {
            var node = mapper.createObjectNode();
            node.put("type", "snapshot");
            node.set("vehicles", mapper.valueToTree(snapshot()));
            return node.toString();
        } catch (Exception e) {
            return "{\"type\":\"snapshot\",\"vehicles\":[]}";
        }
    }
}
