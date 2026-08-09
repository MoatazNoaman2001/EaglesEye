package io.eagleseye.core.geofence;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Geofence CRUD (T-605, FR-GEO-01/02). Geometry is built in PostGIS:
 * circles via geography buffer (metre-accurate), polygons from client points.
 * The pipeline's evaluator reads the same table (ZoneCache).
 *
 * Console drawing tools consume/produce the same DTOs; geometry itself never
 * travels raw — clients speak center+radius or point lists, PostGIS speaks WKT.
 */
@Path("/api/v1/geofences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceResource {

    public record PointDto(double lat, double lon) {}
    public record GeofenceRequest(String name, String category, String color,
                                  String areaType,           // circle | polygon
                                  PointDto center, Double radiusM,
                                  List<PointDto> points) {}
    public record GeofenceDto(UUID id, String name, String category, String color,
                              String areaType, PointDto center, Double radiusM,
                              String geoJson) {}

    @Inject
    EntityManager em;

    @GET
    @SuppressWarnings("unchecked")
    public List<GeofenceDto> list() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, name, category, color, area_type, center_lat, center_lon, radius_m,
                       ST_AsGeoJSON(geom)
                FROM geofences ORDER BY created_at
                """).getResultList();
        return rows.stream().map(GeofenceResource::toDto).collect(Collectors.toList());
    }

    @POST
    @Transactional
    public Response create(GeofenceRequest req) {
        validate(req);
        UUID id = UUID.randomUUID();
        if ("circle".equals(req.areaType())) {
            em.createNativeQuery("""
                    INSERT INTO geofences (id, name, category, color, area_type, center_lat, center_lon, radius_m, geom)
                    VALUES (?1, ?2, ?3, ?4, 'circle', ?5, ?6, ?7,
                            ST_Buffer(ST_SetSRID(ST_MakePoint(?6, ?5), 4326)::geography, ?7)::geometry)
                    """)
                    .setParameter(1, id).setParameter(2, req.name().trim())
                    .setParameter(3, req.category()).setParameter(4, req.color())
                    .setParameter(5, req.center().lat()).setParameter(6, req.center().lon())
                    .setParameter(7, req.radiusM())
                    .executeUpdate();
        } else {
            em.createNativeQuery("""
                    INSERT INTO geofences (id, name, category, color, area_type, geom)
                    VALUES (?1, ?2, ?3, ?4, 'polygon', ST_GeomFromText(?5, 4326))
                    """)
                    .setParameter(1, id).setParameter(2, req.name().trim())
                    .setParameter(3, req.category()).setParameter(4, req.color())
                    .setParameter(5, toPolygonWkt(req.points()))
                    .executeUpdate();
        }
        return Response.status(Response.Status.CREATED).entity(get(id)).build();
    }

    @GET
    @Path("/{id}")
    public GeofenceDto get(@PathParam("id") UUID id) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, name, category, color, area_type, center_lat, center_lon, radius_m,
                       ST_AsGeoJSON(geom)
                FROM geofences WHERE id = ?1
                """).setParameter(1, id).getResultList();
        if (rows.isEmpty()) throw new NotFoundException();
        return toDto(rows.get(0));
    }

    /** Metadata only — reshaping means delete + create (keeps event history unambiguous). */
    @PUT
    @Path("/{id}")
    @Transactional
    public GeofenceDto update(@PathParam("id") UUID id, GeofenceRequest req) {
        int n = em.createNativeQuery(
                        "UPDATE geofences SET name = COALESCE(?2, name), category = ?3, color = ?4 WHERE id = ?1")
                .setParameter(1, id)
                .setParameter(2, req.name() != null && !req.name().isBlank() ? req.name().trim() : null)
                .setParameter(3, req.category()).setParameter(4, req.color())
                .executeUpdate();
        if (n == 0) throw new NotFoundException();
        return get(id);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") UUID id) {
        if (em.createNativeQuery("DELETE FROM geofences WHERE id = ?1").setParameter(1, id).executeUpdate() == 0) {
            throw new NotFoundException();
        }
    }

    private static void validate(GeofenceRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new WebApplicationException("name is required", 400);
        }
        if ("circle".equals(req.areaType())) {
            if (req.center() == null || req.radiusM() == null || req.radiusM() < 10 || req.radiusM() > 100_000) {
                throw new WebApplicationException("circle needs center and radiusM (10..100000)", 400);
            }
        } else if ("polygon".equals(req.areaType())) {
            if (req.points() == null || req.points().size() < 3) {
                throw new WebApplicationException("polygon needs at least 3 points", 400);
            }
        } else {
            throw new WebApplicationException("areaType must be circle or polygon", 400);
        }
    }

    /** WKT wants lon lat order and a closed ring. */
    private static String toPolygonWkt(List<PointDto> points) {
        StringBuilder sb = new StringBuilder("POLYGON((");
        for (PointDto p : points) {
            sb.append(String.format(Locale.ROOT, "%f %f, ", p.lon(), p.lat()));
        }
        sb.append(String.format(Locale.ROOT, "%f %f", points.get(0).lon(), points.get(0).lat()));
        return sb.append("))").toString();
    }

    private static GeofenceDto toDto(Object[] r) {
        Double lat = r[5] != null ? ((Number) r[5]).doubleValue() : null;
        Double lon = r[6] != null ? ((Number) r[6]).doubleValue() : null;
        return new GeofenceDto(
                (UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                lat != null && lon != null ? new PointDto(lat, lon) : null,
                r[7] != null ? ((Number) r[7]).doubleValue() : null,
                (String) r[8]);
    }
}
