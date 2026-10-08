/**
 * National address IDs: 10 random digits followed by one Damm check digit (ADR 0005).
 *
 * <p>Only the API package is exported; the check-digit implementation stays internal.
 */
module org.ugaddress.nationalid {
    requires static org.jspecify;

    exports org.ugaddress.nationalid;
}
