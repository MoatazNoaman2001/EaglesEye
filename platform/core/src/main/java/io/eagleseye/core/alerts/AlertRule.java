package io.eagleseye.core.alerts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A configurable alert rule (FR-ALT-01/02). Evaluated by the pipeline's RulesEngine. */
@Entity
@Table(name = "alert_rules")
public class AlertRule extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @UuidGenerator
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "dev-tenant";

    @Column(nullable = false)
    public String name;

    /** speeding | geofence_entry | geofence_exit | idle | after_hours | low_battery */
    @Column(nullable = false)
    public String type;

    @Column(nullable = false)
    public String severity = "warning";

    @Column(nullable = false)
    public boolean enabled = true;

    /** JSON string: thresholds, zone filters, time windows, cooldownSeconds. */
    @Column(nullable = false)
    public String params = "{}";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
