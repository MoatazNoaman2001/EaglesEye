package io.eagleseye.core.fleet;

import io.eagleseye.core.tenancy.ActivateTenant;
import io.eagleseye.core.tenancy.TenantContext;
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
import java.util.UUID;

/**
 * Vehicle management (FR-DEV-03). Groups, visibility scoping and auth arrive with
 * T-404/T-403; until then this is the raw tenant-wide CRUD the console will use.
 */
@Path("/api/v1/vehicles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class VehicleResource {

    @Inject
    TenantContext tenant;

    public record VehicleRequest(String plate, String name, String make, String model,
                                 Integer modelYear, String category) {}

    @GET
    public List<Vehicle> list() {
        return Vehicle.listAll();
    }

    @GET
    @Path("/{id}")
    public Vehicle get(@PathParam("id") UUID id) {
        Vehicle v = Vehicle.findById(id);
        if (v == null) throw new NotFoundException();
        return v;
    }

    @POST
    @Transactional
    public Response create(VehicleRequest req) {
        if (req == null || req.plate() == null || req.plate().isBlank()) {
            throw new WebApplicationException("plate is required", 400);
        }
        Vehicle v = new Vehicle();
        v.tenantId = tenant.tenantId();
        apply(v, req);
        v.persist();
        return Response.status(Response.Status.CREATED).entity(v).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Vehicle update(@PathParam("id") UUID id, VehicleRequest req) {
        Vehicle v = Vehicle.findById(id);
        if (v == null) throw new NotFoundException();
        apply(v, req);
        return v;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") UUID id) {
        Vehicle v = Vehicle.findById(id);
        if (v == null) throw new NotFoundException();
        long mounted = Device.count("vehicleId = ?1 and status = ?2", id, Device.STATUS_REGISTERED);
        if (mounted > 0) {
            throw new WebApplicationException("vehicle has " + mounted + " assigned device(s) — unassign first", 409);
        }
        v.delete();
    }

    private static void apply(Vehicle v, VehicleRequest req) {
        v.plate = req.plate() != null ? req.plate().trim() : v.plate;
        v.name = req.name();
        v.make = req.make();
        v.model = req.model();
        v.modelYear = req.modelYear();
        v.category = req.category();
    }
}
