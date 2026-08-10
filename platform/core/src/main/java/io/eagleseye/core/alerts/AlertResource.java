package io.eagleseye.core.alerts;

import io.eagleseye.core.tenancy.ActivateTenant;
import jakarta.transaction.Transactional;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/** Alert history (FR-ALT-07): searchable list + acknowledgement. */
@Path("/api/v1/alerts")
@Produces(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class AlertResource {

    @GET
    public List<Alert> list(@QueryParam("vehicleId") String vehicleId,
                            @QueryParam("limit") @DefaultValue("100") int limit) {
        var sort = Sort.descending("time");
        var query = (vehicleId == null || vehicleId.isBlank())
                ? Alert.findAll(sort)
                : Alert.find("vehicleId", sort, vehicleId);
        return query.page(0, Math.min(Math.max(limit, 1), 500)).list();
    }

    @PUT
    @Path("/{id}/ack")
    @Transactional
    public Alert acknowledge(@PathParam("id") UUID id) {
        Alert alert = Alert.findById(id);
        if (alert == null) throw new NotFoundException();
        alert.acknowledged = true;
        return alert;
    }
}
