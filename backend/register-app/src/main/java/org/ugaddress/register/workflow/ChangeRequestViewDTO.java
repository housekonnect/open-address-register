package org.ugaddress.register.workflow;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

/**
 * A change request as an approver sees it: with what it refers to, a diff of current and proposed values, and
 * whether the caller may decide it.
 *
 * @param request the change request
 * @param target what the change refers to
 * @param diff current and proposed value of each field the change touches
 * @param denial why the caller may not decide it, or {@code null} if they may
 */
public record ChangeRequestViewDTO(ChangeRequestDTO request, Target target, List<Diff> diff,
                                   @Nullable Denial denial) {

    /**
     * Creates the view.
     *
     * @param request request
     * @param target target
     * @param diff diff
     * @param denial denial
     */
    public ChangeRequestViewDTO {
        diff = List.copyOf(diff);
    }

    /** Why a caller may not decide a change request. */
    public enum Denial {
        /** Four-eyes rule: the caller proposed it. */
        OWN_REQUEST,
        /** The caller is not a custodian approver. */
        NOT_APPROVER,
        /** The change lies outside the caller's jurisdiction. */
        OUTSIDE_JURISDICTION,
        /** The change request is already decided. */
        ALREADY_DECIDED
    }

    /** What kind of thing a change refers to. */
    public enum TargetType {
        /** An existing addressable object. */
        OBJECT,
        /** A street. */
        STREET,
        /** A new object at a proposed location. */
        LOCATION
    }

    /**
     * What a change refers to.
     *
     * @param type object, street or location
     * @param label address line, street name or object name
     * @param nationalId national ID of the object, if any
     * @param displayId display form of the national ID, if any
     * @param location location of the object, or the proposed location
     */
    public record Target(TargetType type, String label, @Nullable String nationalId, @Nullable String displayId,
                         @Nullable Point location) {
    }

    /**
     * Current and proposed value of one field.
     *
     * @param field {@code houseNumber} or {@code location}
     * @param current current value, or {@code null} for a new object
     * @param proposed proposed value
     */
    public record Diff(String field, @Nullable String current, @Nullable String proposed) {
    }
}
