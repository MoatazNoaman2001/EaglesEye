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

/** A vehicle or asset (FR-DEV-03). Groups/branches arrive with T-404's group work. */
@Entity
@Table(name = "vehicles")
public class Vehicle extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @UuidGenerator
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "dev-tenant";   // real tenancy lands with T-401

    @Column(nullable = false)
    public String plate;

    public String name;
    public String make;
    public String model;

    @Column(name = "model_year")
    public Integer modelYear;

    public String category;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
