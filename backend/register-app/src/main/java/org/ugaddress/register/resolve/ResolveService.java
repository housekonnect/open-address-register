package org.ugaddress.register.resolve;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.nationalid.NationalId;
import org.ugaddress.register.gazetteer.GazetteerService;
import org.ugaddress.register.register.AddressableObjectDTO;
import org.ugaddress.register.register.RegisterService;
import org.ugaddress.register.resolve.internal.SearchRepository;
import org.ugaddress.register.shared.Cursors;
import org.ugaddress.register.shared.ProblemException;

/**
 * The read side of the register: resolves references, searches and finds what is addressed near a point.
 *
 * <p>A reference is a national ID in any display form, or an alias written as {@code <system>:<value>}. Every
 * result is redacted for the caller: residential entrance coordinates only for partners (ADR 0009).
 */
@Service
@Transactional(readOnly = true)
public class ResolveService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RADIUS_METERS = 500;
    private static final double STREET_DISTANCE_STEP_METERS = 10;
    private static final Pattern NATIONAL_ID_LIKE = Pattern.compile("(?i)^\\s*(demo\\s*)?[0-9][0-9\\s-]*$");

    private final RegisterService register;
    private final GazetteerService gazetteer;
    private final SearchRepository search;

    /**
     * Creates the service.
     *
     * @param register the register
     * @param gazetteer the gazetteer, for admin units of streets
     * @param search search and reverse queries
     */
    public ResolveService(final RegisterService register, final GazetteerService gazetteer,
                          final SearchRepository search) {
        this.register = Objects.requireNonNull(register, "register");
        this.gazetteer = Objects.requireNonNull(gazetteer, "gazetteer");
        this.search = Objects.requireNonNull(search, "search");
    }

    /**
     * Resolves a reference.
     *
     * @param reference national ID or {@code system:value} alias
     * @param partner whether the caller may see residential entrance coordinates
     * @return the resolution, or empty if nothing matches
     * @throws ProblemException with status 400 if the reference is malformed
     */
    public Optional<ResolutionDTO> resolve(final String reference, final boolean partner) {
        final String trimmed = reference.strip();
        final int colon = trimmed.indexOf(':');
        final Optional<UUID> objectId;
        final ResolutionDTO.MatchedBy matchedBy;
        if (colon > 0) {
            final String system = trimmed.substring(0, colon);
            final String value = trimmed.substring(colon + 1);
            if (value.isBlank()) {
                throw ProblemException.badRequest("An alias reference has the form <system>:<value>.");
            }
            objectId = register.findIdByAlias(system, value);
            matchedBy = ResolutionDTO.MatchedBy.ALIAS;
        } else {
            objectId = register.findIdByNationalId(NationalId.parse(trimmed));
            matchedBy = ResolutionDTO.MatchedBy.NATIONAL_ID;
        }
        return objectId.flatMap(id -> register.find(id, partner)).map(o -> new ResolutionDTO(matchedBy, o));
    }

    /**
     * Searches street names, addresses, names, national IDs and aliases, best match first.
     *
     * <p>A query that looks like a national ID (digits with spaces or dashes, optionally {@code DEMO}) is searched
     * as one run of digits, so partial IDs match by prefix; a valid one ranks its object first.
     *
     * @param query the query, at least two characters
     * @param cursor cursor from a previous page, or {@code null}
     * @param limit page size, 1 to 100
     * @param partner whether the caller may see residential entrance coordinates
     * @return one page of results
     */
    public SearchPageDTO search(final String query, final @Nullable String cursor, final int limit,
                                final boolean partner) {
        final int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        final boolean idLike = NATIONAL_ID_LIKE.matcher(query).matches();
        final String text = idLike ? query.replaceAll("(?i)demo|[\\s-]", "") : query.strip();
        final String nationalId = NationalId.tryParse(query).map(NationalId::digits).orElse(null);
        final List<SearchRepository.SearchHit> hits = search.search(text, nationalId, decodeSearchCursor(cursor),
            size + 1);
        final boolean more = hits.size() > size;
        final List<SearchRepository.SearchHit> page = more ? hits.subList(0, size) : hits;
        final List<AddressableObjectDTO> items = page.stream()
            .map(hit -> register.find(hit.objectId(), partner))
            .flatMap(Optional::stream)
            .toList();
        final String next = more ? encodeSearchCursor(page.getLast().position()) : null;
        return new SearchPageDTO(items, next);
    }

    /**
     * Finds what is addressed within a radius of a point, nearest first. Non-partners get streets only.
     *
     * @param latitude latitude (EPSG:4326)
     * @param longitude longitude (EPSG:4326)
     * @param radiusMeters radius, 1 to 500 metres
     * @param cursor cursor from a previous page, or {@code null}
     * @param limit page size, 1 to 100
     * @param partner whether the caller gets objects (with residential entrance coordinates)
     * @return one page of matches
     */
    public ReversePageDTO reverse(final double latitude, final double longitude, final int radiusMeters,
                                  final @Nullable String cursor, final int limit, final boolean partner) {
        final int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        final int radius = Math.clamp(radiusMeters, 1, MAX_RADIUS_METERS);
        final OptionalLong after = Cursors.decode(cursor);
        final long offset = after.orElse(0);
        if (offset < 0) {
            throw ProblemException.badRequest("Invalid cursor.");
        }
        final List<ReversePageDTO.Match> matches = new ArrayList<>();
        final boolean more;
        if (partner) {
            final List<SearchRepository.ObjectHit> hits = search.nearestObjects(longitude, latitude, radius, offset,
                size + 1);
            more = hits.size() > size;
            for (final SearchRepository.ObjectHit hit : more ? hits.subList(0, size) : hits) {
                register.find(hit.objectId(), true)
                    .filter(object -> object.address() != null)
                    .ifPresent(object -> matches.add(objectMatch(object, hit.distanceMeters())));
            }
        } else {
            final List<SearchRepository.StreetHit> hits = search.nearestStreets(longitude, latitude, radius, offset,
                size + 1);
            more = hits.size() > size;
            for (final SearchRepository.StreetHit hit : more ? hits.subList(0, size) : hits) {
                matches.add(new ReversePageDTO.Match(ReversePageDTO.Precision.STREET,
                    Math.round(hit.distanceMeters() / STREET_DISTANCE_STEP_METERS) * STREET_DISTANCE_STEP_METERS,
                    hit.id(), hit.name(), hit.postcode(), gazetteer.adminUnitChain(hit.adminUnitId()), null));
            }
        }
        return new ReversePageDTO(matches, more ? Cursors.encode(offset + size) : null);
    }

    private static ReversePageDTO.Match objectMatch(final AddressableObjectDTO object, final double distanceMeters) {
        final AddressableObjectDTO.Address address = Objects.requireNonNull(object.address(), "address");
        return new ReversePageDTO.Match(ReversePageDTO.Precision.OBJECT, distanceMeters, address.thoroughfareId(),
            address.thoroughfareName(), address.postcode(), address.adminUnits(), object);
    }

    private static String encodeSearchCursor(final SearchRepository.Position position) {
        return Cursors.encodeKey(position.score().toPlainString() + "|" + position.sortKey());
    }

    private static SearchRepository.@Nullable Position decodeSearchCursor(final @Nullable String cursor) {
        return Cursors.decodeKey(cursor).map(key -> {
            final int separator = key.indexOf('|');
            if (separator < 1) {
                throw ProblemException.badRequest("Invalid cursor.");
            }
            try {
                return new SearchRepository.Position(new BigDecimal(key.substring(0, separator)),
                    key.substring(separator + 1));
            } catch (final NumberFormatException e) {
                throw ProblemException.badRequest("Invalid cursor.");
            }
        }).orElse(null);
    }

    /**
     * Result of resolving a reference.
     *
     * @param matchedBy how the reference matched
     * @param object the matched object
     */
    public record ResolutionDTO(MatchedBy matchedBy, AddressableObjectDTO object) {

        /** How a reference matched. */
        public enum MatchedBy {
            /** By national ID. */
            NATIONAL_ID,
            /** By alias. */
            ALIAS
        }
    }
}
