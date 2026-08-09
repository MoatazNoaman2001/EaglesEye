package io.eagleseye.core.settings;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.BadRequestException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Admin API for runtime configuration (Arch §7). The console settings screen (T-411)
 * is a thin client over these endpoints.
 *
 * TODO(T-403): restrict to Platform Admin role once OIDC is enabled.
 */
@Path("/api/v1/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {

    public record SettingUpdate(String value) {}

    @GET
    public List<PlatformSetting> list() {
        return PlatformSetting.listAll();
    }

    @PUT
    @Path("/{key}")
    @Transactional
    public PlatformSetting update(@PathParam("key") String key, SettingUpdate update) {
        if (update == null || update.value() == null || update.value().isBlank()) {
            throw new BadRequestException("value is required");
        }
        PlatformSetting setting = PlatformSetting.findById(key);
        if (setting == null) {
            // settings are declared by migrations, never invented by clients
            throw new NotFoundException("unknown setting: " + key);
        }
        validate(setting, update.value().trim());
        setting.value = update.value().trim();
        setting.updatedAt = OffsetDateTime.now();
        return setting;
    }

    private void validate(PlatformSetting setting, String value) {
        switch (setting.valueType) {
            case "int" -> {
                try {
                    if (Integer.parseInt(value) < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new BadRequestException(setting.key + " must be a non-negative integer");
                }
            }
            case "bool" -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new BadRequestException(setting.key + " must be true or false");
                }
            }
            default -> { /* string: anything non-blank is fine */ }
        }
    }
}
