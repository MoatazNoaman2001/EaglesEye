package io.eagleseye.core.alerts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Alert history row (FR-ALT-07), written by the pipeline when a rule fires. */
@Entity
@Table(name = "alerts")
public class Alert extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id")
    public String tenantId;

    @Column(name = "vehicle_id")
    public String vehicleId;

    @Column(name = "vehicle_label")
    public String vehicleLabel;

    @Column(name = "rule_id")
    public UUID ruleId;

    @Column(name = "rule_name")
    public String ruleName;

    public String type;
    public String severity;
    public String message;

    @Column(name = "time")
    public OffsetDateTime time;

    public String context;

    public boolean acknowledged;
}
