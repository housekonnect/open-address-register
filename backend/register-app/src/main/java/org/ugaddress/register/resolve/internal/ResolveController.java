package org.ugaddress.register.resolve.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.ugaddress.api.v1.ResolveApi;
import org.ugaddress.api.v1.model.AddressPage;
import org.ugaddress.api.v1.model.Resolution;
import org.ugaddress.register.register.ObjectApiMapper;
import org.ugaddress.register.resolve.ResolveService;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.ProblemException;

/**
 * {@code /v1/resolve}, {@code /v1/search} and {@code /v1/reverse}. Search and reverse are not implemented yet.
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
        throw ProblemException.notImplemented("search");
    }

    @Override
    public ResponseEntity<AddressPage> reverse(final Double lat, final Double lon, final @Nullable String cursor,
                                               final Integer limit) {
        throw ProblemException.notImplemented("reverse");
    }
}
