package org.ugaddress.register.gazetteer;

import java.util.Objects;
import java.util.UUID;

/**
 * A street or other thoroughfare.
 *
 * @param id thoroughfare id
 * @param name street name
 * @param adminUnitId admin unit that maintains it
 */
public record ThoroughfareDTO(UUID id, String name, UUID adminUnitId) {

    /**
     * Creates the DTO.
     *
     * @param id id
     * @param name name
     * @param adminUnitId admin unit
     */
    public ThoroughfareDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(adminUnitId, "adminUnitId");
    }
}
