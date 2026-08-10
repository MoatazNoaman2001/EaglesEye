package io.eagleseye.core.fleet;

import io.eagleseye.core.tenancy.ActivateTenant;
import io.eagleseye.core.tenancy.TenantContext;
import jakarta.transaction.Transactional;
import io.eagleseye.core.bridge.TraccarBridgeClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Device lifecycle (FR-DEV-01/02/06): register -> assign -> unassign -> decommission.
 * Registration mirrors into the Traccar bridge (T-205); decommission removes it there
 * but NEVER deletes our row — history is forever.
 */
@Path("/api/v1/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ActivateTenant
@Transactional
public class DeviceResource {

    public record DeviceRequest(String imei, String protocol, String model, String simMsisdn, String notes) {}

    @Inject
    TraccarBridgeClient bridge;

    @Inject
    RegistryPublisher registry;

    @Inject
    TenantContext tenant;

    @GET
    public List<Device> list() {
        return Device.listAll();
    }

    @GET
    @Path("/{id}")
    public Device get(@PathParam("id") UUID id) {
        Device d = Device.findById(id);
        if (d == null) throw new NotFoundException();
        return d;
    }

    @POST
    @Transactional
    public Response register(DeviceRequest req) {
        if (req == null || req.imei() == null || req.imei().isBlank()) {
            throw new WebApplicationException("imei is required", 400);
        }
        String imei = req.imei().trim();
        if (Device.count("imei", imei) > 0) {
            throw new WebApplicationException("device with this IMEI already exists", 409);
        }
        Device d = new Device();
        d.tenantId = tenant.tenantId();
        d.imei = imei;
        d.protocol = req.protocol();
        d.model = req.model();
        d.simMsisdn = req.simMsisdn();
        d.notes = req.notes();
        // mirror into the bridge (ADR-9); traccarId stays null on failure and
        // reconciliation (T-301) repairs it — our registry is the truth
        d.traccarId = bridge.createDevice(imei, imei).orElse(null);
        d.persist();
        registry.publish(d);
        return Response.status(Response.Status.CREATED).entity(d).build();
    }

    @PUT
    @Path("/{id}/assign/{vehicleId}")
    @Transactional
    public Device assign(@PathParam("id") UUID id, @PathParam("vehicleId") UUID vehicleId) {
        Device d = active(id);
        if (Vehicle.findById(vehicleId) == null) throw new WebApplicationException("unknown vehicle", 404);
        closeOpenAssignment(d);
        d.vehicleId = vehicleId;
        DeviceAssignment a = new DeviceAssignment();
        a.deviceId = d.id;
        a.vehicleId = vehicleId;
        a.persist();
        registry.publish(d);
        return d;
    }

    @PUT
    @Path("/{id}/unassign")
    @Transactional
    public Device unassign(@PathParam("id") UUID id) {
        Device d = active(id);
        closeOpenAssignment(d);
        d.vehicleId = null;
        registry.publish(d);
        return d;
    }

    @PUT
    @Path("/{id}/decommission")
    @Transactional
    public Device decommission(@PathParam("id") UUID id) {
        Device d = active(id);
        closeOpenAssignment(d);
        d.vehicleId = null;
        d.status = Device.STATUS_DECOMMISSIONED;
        d.decommissionedAt = OffsetDateTime.now();
        if (d.traccarId != null) {
            bridge.deleteDevice(d.traccarId);   // bridge stops accepting it (FR-ING-06)
            d.traccarId = null;
        }
        registry.publish(d);
        return d;
    }

    private static Device active(UUID id) {
        Device d = Device.findById(id);
        if (d == null) throw new NotFoundException();
        if (Device.STATUS_DECOMMISSIONED.equals(d.status)) {
            throw new WebApplicationException("device is decommissioned", 409);
        }
        return d;
    }

    private static void closeOpenAssignment(Device d) {
        if (d.vehicleId != null) {
            DeviceAssignment open = DeviceAssignment
                    .find("deviceId = ?1 and unassignedAt is null", d.id).firstResult();
            if (open != null) open.unassignedAt = OffsetDateTime.now();
        }
    }
}
