package org.ugaddress.register.register;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;
import org.ugaddress.register.gazetteer.AdminUnitDTO;

/**
 * An addressable object as seen by one caller. Residential entrance coordinates are already removed unless the
 * caller is a partner.
 *
 * @param id object id
 * @param nationalId the 11-digit national ID
 * @param demonstration whether the ID is a demonstration ID (synthetic data)
 * @param kind {@code building}, {@code entrance}, {@code access_point}, {@code landmark} or {@code facility}
 * @param lifecycle {@code proposed}, {@code active} or {@code retired}
 * @param name name of a landmark or facility
 * @param location representative point, or {@code null} if redacted
 * @param address the active address, if any
 * @param entrances entrances of the object
 * @param aliases identifiers of the object in other systems
 * @param version row version
 * @param validFrom start of business validity
 * @param adminUnitId admin unit the object belongs to
 */
public record AddressableObjectDTO(UUID id, String nationalId, boolean demonstration, String kind, String lifecycle,
                                   @Nullable String name, @Nullable Point location, @Nullable Address address,
                                   List<Entrance> entrances, List<Alias> aliases, long version,
                                   OffsetDateTime validFrom, UUID adminUnitId) {

    /**
     * Creates the DTO.
     *
     * @param id id
     * @param nationalId national ID
     * @param demonstration demonstration flag
     * @param kind kind
     * @param lifecycle lifecycle
     * @param name name
     * @param location location
     * @param address address
     * @param entrances entrances
     * @param aliases aliases
     * @param version version
     * @param validFrom valid from
     * @param adminUnitId admin unit
     */
    public AddressableObjectDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nationalId, "nationalId");
        entrances = List.copyOf(entrances);
        aliases = List.copyOf(aliases);
    }

    /**
     * An address.
     *
     * @param id address id
     * @param houseNumber house number
     * @param unit unit within the building
     * @param thoroughfareId street id
     * @param thoroughfareName street name
     * @param postcode postcode
     * @param adminUnits admin units, smallest first
     */
    public record Address(UUID id, String houseNumber, @Nullable String unit, UUID thoroughfareId,
                          String thoroughfareName, @Nullable String postcode, List<AdminUnitDTO> adminUnits) {

        /**
         * Creates the address.
         *
         * @param id id
         * @param houseNumber house number
         * @param unit unit
         * @param thoroughfareId street id
         * @param thoroughfareName street name
         * @param postcode postcode
         * @param adminUnits admin units
         */
        public Address {
            adminUnits = List.copyOf(adminUnits);
        }
    }

    /**
     * An entrance of the object.
     *
     * @param id entrance id
     * @param nationalId national ID of the entrance
     * @param demonstration whether the ID is a demonstration ID
     * @param residential whether it is a residential entrance
     * @param main whether it is the main entrance
     * @param location location, or {@code null} if redacted
     */
    public record Entrance(UUID id, String nationalId, boolean demonstration, boolean residential, boolean main,
                           @Nullable Point location) {
    }

    /**
     * An identifier of the object in another system.
     *
     * @param system issuing system
     * @param value identifier value
     */
    public record Alias(String system, String value) {
    }
}
