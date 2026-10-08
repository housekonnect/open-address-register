package org.ugaddress.register.field;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

/**
 * A capture made in the field.
 *
 * @param kind kind of the captured object, e.g. {@code building}
 * @param location captured point
 * @param targetObjectId existing object the capture refers to, if any
 * @param note free text about the place; must not contain personal data
 * @param photo photo bytes
 * @param photoContentType {@code image/jpeg} or {@code image/png}
 */
public record NewFieldCaptureDTO(String kind, Point location, @Nullable UUID targetObjectId, @Nullable String note,
                                 byte[] photo, String photoContentType) {

    /**
     * Creates the DTO.
     *
     * @param kind kind
     * @param location location
     * @param targetObjectId target object
     * @param note note
     * @param photo photo
     * @param photoContentType content type
     */
    public NewFieldCaptureDTO {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        photo = photo.clone();
        Objects.requireNonNull(photoContentType, "photoContentType");
    }

    @Override
    public byte[] photo() {
        return photo.clone();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof final NewFieldCaptureDTO that && kind.equals(that.kind)
            && location.equals(that.location) && Objects.equals(targetObjectId, that.targetObjectId)
            && Objects.equals(note, that.note) && Arrays.equals(photo, that.photo)
            && photoContentType.equals(that.photoContentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, location, targetObjectId, note, Arrays.hashCode(photo), photoContentType);
    }

    @Override
    public String toString() {
        return "NewFieldCaptureDTO[kind=" + kind + ", photoBytes=" + photo.length + "]";
    }
}
