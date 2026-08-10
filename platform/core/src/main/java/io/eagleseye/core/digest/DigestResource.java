package io.eagleseye.core.digest;

import io.eagleseye.core.tenancy.ActivateTenant;
import io.eagleseye.core.tenancy.TenantContext;
import jakarta.transaction.Transactional;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Digest history + on-demand generation (demos, backfills). */
@Path("/api/v1/digests")
@Produces(MediaType.APPLICATION_JSON)
public class DigestResource {

    @Entity
    @Table(name = "digests")
    public static class Digest extends PanacheEntityBase {
        @Id
        public UUID id;
        @Column(name = "tenant_id")
        public String tenantId;
        @Column(name = "digest_date")
        public LocalDate digestDate;
        @Column(name = "text_ar")
        public String textAr;
        @Column(name = "text_en")
        public String textEn;
        public String stats;
    }

    @Inject
    DigestService service;

    @Inject
    TenantContext tenant;

    @GET
    @ActivateTenant
    @Transactional
    public List<Digest> list(@QueryParam("limit") @DefaultValue("30") int limit) {
        return Digest.findAll(Sort.descending("digestDate"))
                .page(0, Math.min(Math.max(limit, 1), 90)).list();
    }

    @POST
    @Path("/generate")
    public Map<String, String> generate(@QueryParam("date") String date) {
        LocalDate target = date != null && !date.isBlank()
                ? LocalDate.parse(date)
                : LocalDate.now(ZoneId.of("Africa/Cairo")).minusDays(1);
        try {
            return service.generate(tenant.tenantId(), target);
        } catch (Exception e) {
            throw new WebApplicationException("digest generation failed: " + e.getMessage(), 500);
        }
    }
}
