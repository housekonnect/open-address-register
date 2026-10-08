package org.ugaddress.register.gazetteer;

import java.util.Objects;
import java.util.UUID;

/**
 * An administrative unit.
 *
 * @param id unit id
 * @param level level such as {@code district} or {@code parish}
 * @param code stable code
 * @param name display name
 */
public record AdminUnitDTO(UUID id, String level, String code, String name) {

    /**
     * Creates the DTO.
     *
     * @param id id
     * @param level level
     * @param code code
     * @param name name
     */
    public AdminUnitDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
