package io.eagleseye.core.trips;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A stop between trips (FR-TRP-04), written by the pipeline's TripEngine. */
@Entity
@Table(name = "stops")
public class Stop extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id")
    public String tenantId;

    @Column(name = "vehicle_id")
    public String vehicleId;

    @Column(name = "device_imei")
    public String deviceImei;

    public OffsetDateTime arrival;
    public OffsetDateTime departure;

    @Column(name = "duration_seconds")
    public Integer durationSeconds;

    public Double latitude;
    public Double longitude;
}
