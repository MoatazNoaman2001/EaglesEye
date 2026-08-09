package io.eagleseye.core.settings;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One runtime-configurable operational parameter (Arch §7, runtime configuration service).
 * Seeded by Flyway migration V1; edited from the console admin screens (T-411).
 */
@Entity
@Table(name = "platform_settings")
public class PlatformSetting extends PanacheEntityBase {

    @Id
    @Column(name = "key")
    public String key;

    @Column(name = "value", nullable = false)
    public String value;

    /** string | int | bool — lets the UI render the right input and validate. */
    @Column(name = "value_type", nullable = false)
    public String valueType;

    @Column(name = "description", nullable = false)
    public String description;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
