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
 * @param photoSha256 SHA-256 of the photo computed on the device, lower-case hex
 * @param locationMocked whether the device reported the location as mocked (accepted and flagged)
 */
public record NewFieldCaptureDTO(String kind, Point location, @Nullable UUID targetObjectId, @Nullable String note,
                                 byte[] photo, String photoContentType, String photoSha256,
                                 boolean locationMocked) {

    /**
     * Creates the DTO.
     *
     * @param kind kind
     * @param location location
     * @param targetObjectId target object
     * @param note note
     * @param photo photo
     * @param photoContentType content type
     * @param photoSha256 device-side SHA-256 of the photo
     * @param locationMocked mocked-location flag
     */
    public NewFieldCaptureDTO {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        photo = photo.clone();
        Objects.requireNonNull(photoContentType, "photoContentType");
        Objects.requireNonNull(photoSha256, "photoSha256");
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
            && photoContentType.equals(that.photoContentType) && photoSha256.equals(that.photoSha256)
            && locationMocked == that.locationMocked;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, location, targetObjectId, note, Arrays.hashCode(photo), photoContentType,
            photoSha256, locationMocked);
    }

    @Override
    public String toString() {
        return "NewFieldCaptureDTO[kind=" + kind + ", photoBytes=" + photo.length + "]";
    }
}
