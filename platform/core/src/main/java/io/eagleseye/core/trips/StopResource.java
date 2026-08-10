package io.eagleseye.core.trips;

import io.eagleseye.core.tenancy.ActivateTenant;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Stop report (FR-TRP-04): where and how long vehicles parked between trips. */
@Path("/api/v1/stops")
@Produces(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class StopResource {

    @GET
    public List<Stop> list(@QueryParam("vehicleId") String vehicleId,
                           @QueryParam("limit") @DefaultValue("100") int limit) {
        var sort = Sort.descending("arrival");
        var query = (vehicleId == null || vehicleId.isBlank())
                ? Stop.findAll(sort)
                : Stop.find("vehicleId", sort, vehicleId);
        return query.page(0, Math.min(Math.max(limit, 1), 500)).list();
    }
}
