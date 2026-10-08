package org.ugaddress.register.shared;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Conversions between JTS points (EPSG:4326) and GeoJSON points of the API contract.
 */
public final class GeoJson {

    /** Spatial reference of all register geometry. */
    public static final int SRID = 4326;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    private GeoJson() {
    }

    /**
     * Creates a point.
     *
     * @param longitude longitude in degrees, -180 to 180
     * @param latitude latitude in degrees, -90 to 90
     * @return the point in EPSG:4326
     * @throws ProblemException with status 400 if a coordinate is out of range
     */
    public static Point point(final double longitude, final double latitude) {
        if (!(longitude >= -180 && longitude <= 180) || !(latitude >= -90 && latitude <= 90)) {
            throw ProblemException.badRequest("Coordinates must be longitude -180..180 and latitude -90..90.");
        }
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    /**
     * Converts an API point to a JTS point.
     *
     * @param point GeoJSON point with {@code [longitude, latitude]}
     * @return the JTS point
     * @throws ProblemException with status 400 if the point is malformed
     */
    public static Point fromApi(final org.ugaddress.api.v1.model.Point point) {
        final List<Double> coordinates = point.getCoordinates();
        if (coordinates.size() != 2 || coordinates.get(0) == null || coordinates.get(1) == null) {
            throw ProblemException.badRequest("A point has exactly two coordinates: [longitude, latitude].");
        }
        return point(coordinates.get(0), coordinates.get(1));
    }

    /**
     * Converts a JTS point to an API point.
     *
     * @param point the point, or {@code null}
     * @return the GeoJSON point, or {@code null}
     */
    public static org.ugaddress.api.v1.model.@Nullable Point toApi(final @Nullable Point point) {
        if (point == null) {
            return null;
        }
        return new org.ugaddress.api.v1.model.Point(org.ugaddress.api.v1.model.Point.TypeEnum.POINT,
            List.of(point.getX(), point.getY()));
    }
}
