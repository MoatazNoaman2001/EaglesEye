package io.eagleseye.core.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Alert rule management (FR-ALT-02). The pipeline picks changes up within 60 s. */
@Path("/api/v1/alert-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertRuleResource {

    private static final Set<String> TYPES =
            Set.of("speeding", "geofence_entry", "geofence_exit", "idle", "after_hours", "low_battery");

    public record RuleRequest(String name, String type, String severity, Boolean enabled, Object params) {}

    @Inject
    ObjectMapper mapper;

    @GET
    public List<AlertRule> list() {
        return AlertRule.listAll();
    }

    @POST
    @Transactional
    public Response create(RuleRequest req) {
        AlertRule rule = new AlertRule();
        apply(rule, req, true);
        rule.persist();
        return Response.status(Response.Status.CREATED).entity(rule).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public AlertRule update(@PathParam("id") UUID id, RuleRequest req) {
        AlertRule rule = AlertRule.findById(id);
        if (rule == null) throw new NotFoundException();
        apply(rule, req, false);
        return rule;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") UUID id) {
        if (!AlertRule.deleteById(id)) throw new NotFoundException();
    }

    private void apply(AlertRule rule, RuleRequest req, boolean creating) {
        if (creating) {
            if (req == null || req.name() == null || req.name().isBlank()) {
                throw new WebApplicationException("name is required", 400);
            }
            if (req.type() == null || !TYPES.contains(req.type())) {
                throw new WebApplicationException("type must be one of " + TYPES, 400);
            }
        }
        if (req.name() != null && !req.name().isBlank()) rule.name = req.name().trim();
        if (req.type() != null) {
            if (!TYPES.contains(req.type())) throw new WebApplicationException("bad type", 400);
            rule.type = req.type();
        }
        if (req.severity() != null) rule.severity = req.severity();
        if (req.enabled() != null) rule.enabled = req.enabled();
        if (req.params() != null) {
            try {
                rule.params = mapper.writeValueAsString(req.params());
            } catch (Exception e) {
                throw new WebApplicationException("params must be a JSON object", 400);
            }
        }
    }
}
