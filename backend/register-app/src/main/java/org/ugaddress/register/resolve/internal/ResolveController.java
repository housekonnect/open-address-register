package org.ugaddress.register.resolve.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.ugaddress.api.v1.ResolveApi;
import org.ugaddress.api.v1.model.AddressPage;
import org.ugaddress.api.v1.model.Resolution;
import org.ugaddress.api.v1.model.ReverseMatch;
import org.ugaddress.api.v1.model.ReversePage;
import org.ugaddress.api.v1.model.ThoroughfareRef;
import org.ugaddress.register.register.ObjectApiMapper;
import org.ugaddress.register.resolve.ResolveService;
import org.ugaddress.register.resolve.ReversePageDTO;
import org.ugaddress.register.resolve.SearchPageDTO;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.ProblemException;

/**
 * {@code /v1/resolve}, {@code /v1/search} and {@code /v1/reverse}.
 */
@RestController
class ResolveController implements ResolveApi {

    private final ResolveService resolveService;
    private final CurrentActorService actors;

    ResolveController(final ResolveService resolveService, final CurrentActorService actors) {
        this.resolveService = resolveService;
        this.actors = actors;
    }

    @Override
    public ResponseEntity<Resolution> resolve(final String ref, final @Nullable String ifNoneMatch) {
        return resolveService.resolve(ref, actors.current().partner())
            .map(r -> new Resolution(
                r.matchedBy() == ResolveService.ResolutionDTO.MatchedBy.ALIAS
                    ? Resolution.MatchedByEnum.ALIAS : Resolution.MatchedByEnum.NATIONAL_ID,
                ObjectApiMapper.toApi(r.object())))
            .map(ResponseEntity::ok)
            .orElseThrow(() -> ProblemException.notFound("Nothing in the register matches this reference."));
    }

    @Override
    public ResponseEntity<AddressPage> search(final String q, final @Nullable String cursor, final Integer limit) {
        final SearchPageDTO page = resolveService.search(q, cursor, limit, actors.current().partner());
        final AddressPage api = new AddressPage(page.items().stream().map(ObjectApiMapper::toApi).toList());
        api.setNextCursor(page.nextCursor());
        return ResponseEntity.ok(api);
    }

    @Override
    public ResponseEntity<ReversePage> reverse(final Double lat, final Double lon, final Integer radius,
                                               final @Nullable String cursor, final Integer limit) {
        final ReversePageDTO page = resolveService.reverse(lat, lon, radius, cursor, limit,
            actors.current().partner());
        final ReversePage api = new ReversePage(page.items().stream().map(ResolveController::match).toList());
        api.setNextCursor(page.nextCursor());
        return ResponseEntity.ok(api);
    }

    private static ReverseMatch match(final ReversePageDTO.Match match) {
        final ReverseMatch api = new ReverseMatch(
            match.precision() == ReversePageDTO.Precision.OBJECT
                ? ReverseMatch.PrecisionEnum.OBJECT : ReverseMatch.PrecisionEnum.STREET,
            match.distanceMeters(), new ThoroughfareRef(match.thoroughfareId(), match.thoroughfareName()),
            ObjectApiMapper.adminUnits(match.adminUnits()));
        api.setPostcode(match.postcode());
        if (match.object() != null) {
            api.setObject(ObjectApiMapper.toApi(match.object()));
        }
        return api;
    }
}
