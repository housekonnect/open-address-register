package org.ugaddress.register.gazetteer;

import java.util.Objects;
import java.util.UUID;

/**
 * An organisation that maintains part of the register.
 *
 * @param id custodian id
 * @param code stable code, delivered in the {@code custodian} token claim
 * @param name organisation name (never a person's name)
 */
public record CustodianDTO(UUID id, String code, String name) {

    /**
     * Creates the DTO.
     *
     * @param id id
     * @param code code
     * @param name name
     */
    public CustodianDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
