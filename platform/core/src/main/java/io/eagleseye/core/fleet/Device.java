package io.eagleseye.core.fleet;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A GPS device (FR-DEV-01). Never deleted — decommissioning keeps history (FR-DEV-06).
 * `traccarId` mirrors the device into the bridge (ADR-9 registry sync, T-205).
 */
@Entity
@Table(name = "devices")
public class Device extends PanacheEntityBase {

    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_DECOMMISSIONED = "DECOMMISSIONED";

    @Id
    @GeneratedValue
    @UuidGenerator
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "dev-tenant";

    @Column(nullable = false, unique = true)
    public String imei;

    public String protocol;
    public String model;

    @Column(name = "sim_msisdn")
    public String simMsisdn;

    @Column(nullable = false)
    public String status = STATUS_REGISTERED;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    @Column(name = "traccar_id")
    public Integer traccarId;

    public String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "decommissioned_at")
    public OffsetDateTime decommissionedAt;
}
