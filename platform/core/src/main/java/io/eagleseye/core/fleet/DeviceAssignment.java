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

/** Assignment history row (FR-DEV-02): which device was on which vehicle, when. */
@Entity
@Table(name = "device_assignments")
public class DeviceAssignment extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @UuidGenerator
    public UUID id;

    @Column(name = "device_id", nullable = false)
    public UUID deviceId;

    @Column(name = "vehicle_id", nullable = false)
    public UUID vehicleId;

    @Column(name = "assigned_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime assignedAt;

    @Column(name = "unassigned_at")
    public OffsetDateTime unassignedAt;
}
