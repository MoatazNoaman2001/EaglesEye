package io.eagleseye.core.trips;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model over the trips table (written by the pipeline's TripEngine). */
@Entity
@Table(name = "trips")
public class Trip extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id")
    public String tenantId;

    @Column(name = "vehicle_id")
    public String vehicleId;

    @Column(name = "device_imei")
    public String deviceImei;

    @Column(name = "start_time")
    public OffsetDateTime startTime;

    @Column(name = "end_time")
    public OffsetDateTime endTime;

    @Column(name = "start_lat")
    public Double startLat;

    @Column(name = "start_lon")
    public Double startLon;

    @Column(name = "end_lat")
    public Double endLat;

    @Column(name = "end_lon")
    public Double endLon;

    @Column(name = "distance_km")
    public Float distanceKm;

    @Column(name = "max_speed_kmh")
    public Float maxSpeedKmh;

    @Column(name = "position_count")
    public Integer positionCount;
}
