package org.ugaddress.register.workflow;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

/**
 * A proposed change, before it is stored.
 *
 * @param kind {@code correction}, {@code new_object} or {@code retirement}
 * @param source {@code console} or {@code field}
 * @param targetObjectId object to correct or retire
 * @param thoroughfareId street the change refers to
 * @param summary what should change and why; must not contain personal data
 * @param proposedLocation proposed point, if any
 * @param proposedHouseNumber proposed house number, if any
 * @param photoObjectKey object-storage key of an attached photo, if any
 * @param photoSha256 verified SHA-256 of the attached photo, if any
 */
public record NewChangeRequestDTO(String kind, String source, @Nullable UUID targetObjectId,
                                  @Nullable UUID thoroughfareId, String summary, @Nullable Point proposedLocation,
                                  @Nullable String proposedHouseNumber, @Nullable String photoObjectKey,
                                  @Nullable String photoSha256) {

    /**
     * Creates the DTO.
     *
     * @param kind kind
     * @param source source
     * @param targetObjectId target object
     * @param thoroughfareId thoroughfare
     * @param summary summary
     * @param proposedLocation location
     * @param proposedHouseNumber house number
     * @param photoObjectKey photo key
     * @param photoSha256 photo hash
     */
    public NewChangeRequestDTO {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(summary, "summary");
    }
}
