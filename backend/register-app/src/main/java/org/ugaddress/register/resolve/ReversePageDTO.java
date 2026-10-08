package org.ugaddress.register.resolve;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.ugaddress.register.gazetteer.AdminUnitDTO;
import org.ugaddress.register.register.AddressableObjectDTO;

/**
 * One page of a reverse lookup, nearest first.
 *
 * @param items the matches
 * @param nextCursor cursor of the next page, or {@code null} on the last page
 */
public record ReversePageDTO(List<Match> items, @Nullable String nextCursor) {

    /**
     * Creates the page.
     *
     * @param items items
     * @param nextCursor next cursor
     */
    public ReversePageDTO {
        items = List.copyOf(items);
    }

    /** How precise a match is: public callers get streets, partners get objects. */
    public enum Precision {
        /** The nearest street; no object, distance rounded to 10 m. */
        STREET,
        /** The nearest addressed object with its full address. */
        OBJECT
    }

    /**
     * One match.
     *
     * @param precision street or object
     * @param distanceMeters distance from the point (rounded to 10 m at street precision)
     * @param thoroughfareId street id
     * @param thoroughfareName street name
     * @param postcode postcode, if known
     * @param adminUnits admin units, smallest first
     * @param object the object, only at object precision
     */
    public record Match(Precision precision, double distanceMeters, UUID thoroughfareId, String thoroughfareName,
                        @Nullable String postcode, List<AdminUnitDTO> adminUnits,
                        @Nullable AddressableObjectDTO object) {

        /**
         * Creates the match.
         *
         * @param precision precision
         * @param distanceMeters distance
         * @param thoroughfareId street id
         * @param thoroughfareName street name
         * @param postcode postcode
         * @param adminUnits admin units
         * @param object object
         */
        public Match {
            adminUnits = List.copyOf(adminUnits);
        }
    }
}
