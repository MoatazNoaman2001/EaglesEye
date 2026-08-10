package io.eagleseye.core.trips;

import io.eagleseye.core.tenancy.ActivateTenant;
import jakarta.transaction.Transactional;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Trip queries (FR-TRP-03/06 foundation). Tenant scoping arrives with T-401;
 * date-range filters and daily summaries grow here with the report suite.
 */
@Path("/api/v1/trips")
@Produces(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class TripResource {

    @GET
    public List<Trip> list(@QueryParam("vehicleId") String vehicleId,
                           @QueryParam("limit") @DefaultValue("50") int limit) {
        var sort = Sort.descending("startTime");
        var query = (vehicleId == null || vehicleId.isBlank())
                ? Trip.findAll(sort)
                : Trip.find("vehicleId", sort, vehicleId);
        return query.page(0, Math.min(Math.max(limit, 1), 500)).list();
    }
}
