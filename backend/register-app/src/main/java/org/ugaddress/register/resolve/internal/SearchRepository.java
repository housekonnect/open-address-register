package org.ugaddress.register.resolve.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Queries behind {@code /v1/search} and {@code /v1/reverse}: full-text and trigram search over
 * {@code register.search_document}, and nearest-first lookups on {@code geography}.
 */
@Repository
public class SearchRepository {

    /** Minimum trigram word similarity for a typo-tolerant match (one typo per word stays well above it). */
    private static final String WORD_SIMILARITY_THRESHOLD = "0.5";

    // The score ranks in tiers: an exact national ID (10), then matches of every query word as full-text prefixes
    // (1), then typo-tolerant trigram matches; the trigram word similarity (0 to 1) orders hits within a tier, and
    // the sort key (street, house number) orders equal scores. ts_rank is deliberately not used: it rewards repeated
    // words, so "Amani Avenue Primary School" would outrank "1 Amani Avenue" for the query "Amani Avenue".
    // The score is rounded so that it round-trips through a cursor.
    private static final String SEARCH = """
        WITH input AS (
            SELECT register.search_normalise(?) AS text,
                   (SELECT to_tsquery('simple', string_agg(token || ':*', ' & '))
                      FROM regexp_split_to_table(register.search_normalise(?), '[^a-z0-9]+') AS token
                     WHERE token <> '') AS query,
                   ?::text AS national_id
        ), scored AS (
            SELECT d.object_id, d.sort_key,
                   round((CASE WHEN i.national_id = ANY (d.national_ids) THEN 10 ELSE 0 END
                        + CASE WHEN d.document @@ i.query THEN 1 ELSE 0 END
                        + word_similarity(i.text, d.search_text))::numeric, 6) AS score
              FROM register.search_document d, input i
             WHERE i.national_id = ANY (d.national_ids) OR d.document @@ i.query OR i.text <% d.search_text
        )
        SELECT object_id, score, sort_key FROM scored
        """;

    private final DSLContext dsl;

    SearchRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Searches the register, best match first.
     *
     * @param text the query text (already normalised by the caller where it is a national ID)
     * @param nationalId the 11 digits if the query is a valid national ID, otherwise {@code null}
     * @param after position of the last hit of the previous page, or {@code null} for the first page
     * @param limit maximum hits
     * @return the hits
     */
    public List<SearchHit> search(final String text, final @Nullable String nationalId, final @Nullable Position after,
                                  final int limit) {
        dsl.execute("SELECT set_config('pg_trgm.word_similarity_threshold', ?, true)", WORD_SIMILARITY_THRESHOLD);
        if (after == null) {
            return dsl.resultQuery(SEARCH + " ORDER BY score DESC, sort_key LIMIT ?", text, text, nationalId, limit)
                .fetch(SearchRepository::hit);
        }
        return dsl.resultQuery(SEARCH + """
                 WHERE score < ? OR (score = ? AND sort_key > ?)
                 ORDER BY score DESC, sort_key LIMIT ?""",
                text, text, nationalId, after.score(), after.score(), after.sortKey(), limit)
            .fetch(SearchRepository::hit);
    }

    private static SearchHit hit(final Record r) {
        return new SearchHit(r.get(0, UUID.class), new Position(r.get(1, BigDecimal.class), r.get(2, String.class)));
    }

    /**
     * Finds the current streets within a radius of a point, nearest first.
     *
     * @param longitude longitude (EPSG:4326)
     * @param latitude latitude (EPSG:4326)
     * @param radiusMeters radius in metres
     * @param offset hits to skip
     * @param limit maximum hits
     * @return the streets with their exact distance
     */
    public List<StreetHit> nearestStreets(final double longitude, final double latitude, final int radiusMeters,
                                          final long offset, final int limit) {
        return dsl.resultQuery("""
                WITH p AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geom)
                SELECT t.id, t.name, t.admin_unit_id,
                       ST_Distance(t.centreline::geography, p.geom::geography) AS distance,
                       (SELECT pa.code FROM register.postcode_area pa
                         WHERE pa.valid_to IS NULL AND ST_Intersects(pa.area, ST_ClosestPoint(t.centreline, p.geom))
                         LIMIT 1) AS postcode
                  FROM register.thoroughfare t, p
                 WHERE t.lifecycle = 'active' AND t.valid_to IS NULL
                   AND ST_DWithin(t.centreline::geography, p.geom::geography, ?)
                 ORDER BY distance, t.id
                 OFFSET ? LIMIT ?""", longitude, latitude, radiusMeters, offset, limit)
            .fetch(r -> new StreetHit(r.get(0, UUID.class), r.get(1, String.class), r.get(2, UUID.class),
                r.get(3, Double.class), r.get(4, String.class)));
    }

    /**
     * Finds the current addressed objects (with an active address) within a radius of a point, nearest first.
     * Entrances are not returned themselves; they come with their building.
     *
     * @param longitude longitude (EPSG:4326)
     * @param latitude latitude (EPSG:4326)
     * @param radiusMeters radius in metres
     * @param offset hits to skip
     * @param limit maximum hits
     * @return the objects with their exact distance
     */
    public List<ObjectHit> nearestObjects(final double longitude, final double latitude, final int radiusMeters,
                                          final long offset, final int limit) {
        return dsl.resultQuery("""
                WITH p AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS geog)
                SELECT o.id, ST_Distance(o.location::geography, p.geog) AS distance
                  FROM register.addressable_object o, p
                 WHERE o.kind <> 'entrance' AND o.lifecycle = 'active' AND o.valid_to IS NULL
                   AND ST_DWithin(o.location::geography, p.geog, ?)
                   AND EXISTS (SELECT 1 FROM register.address a
                                WHERE a.object_id = o.id AND a.lifecycle = 'active' AND a.valid_to IS NULL)
                 ORDER BY distance, o.id
                 OFFSET ? LIMIT ?""", longitude, latitude, radiusMeters, offset, limit)
            .fetch(r -> new ObjectHit(r.get(0, UUID.class), r.get(1, Double.class)));
    }

    /**
     * One search hit.
     *
     * @param objectId the object
     * @param position its position in the ranking
     */
    public record SearchHit(UUID objectId, Position position) {
    }

    /**
     * Position in the search ranking: best score first, then the sort key.
     *
     * @param score the rank score (rounded, so it round-trips through a cursor)
     * @param sortKey the tie-breaker
     */
    public record Position(BigDecimal score, String sortKey) {
    }

    /**
     * A street near a point.
     *
     * @param id thoroughfare id
     * @param name street name
     * @param adminUnitId admin unit of the street
     * @param distanceMeters exact distance
     * @param postcode postcode at the street's point nearest to the query point
     */
    public record StreetHit(UUID id, String name, UUID adminUnitId, double distanceMeters, @Nullable String postcode) {
    }

    /**
     * An addressed object near a point.
     *
     * @param objectId the object
     * @param distanceMeters exact distance
     */
    public record ObjectHit(UUID objectId, double distanceMeters) {
    }
}
