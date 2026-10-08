package org.ugaddress.register.workflow.internal;

import java.net.URI;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.ugaddress.api.v1.ChangesApi;
import org.ugaddress.api.v1.model.ChangeKind;
import org.ugaddress.api.v1.model.ChangePage;
import org.ugaddress.api.v1.model.ChangeRequest;
import org.ugaddress.api.v1.model.ChangeRequestCreate;
import org.ugaddress.api.v1.model.ChangeRequestState;
import org.ugaddress.register.shared.CurrentActor;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.GeoJson;
import org.ugaddress.register.shared.IdempotencyService;
import org.ugaddress.register.shared.ProblemException;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.workflow.ChangeRequestService;
import org.ugaddress.register.workflow.NewChangeRequestDTO;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /v1/change-requests} and the change feed (not implemented yet).
 */
@RestController
class ChangesController implements ChangesApi {

    private final ChangeRequestService changeRequests;
    private final CurrentActorService actors;
    private final JsonMapper jsonMapper;

    ChangesController(final ChangeRequestService changeRequests, final CurrentActorService actors,
                      final JsonMapper jsonMapper) {
        this.changeRequests = changeRequests;
        this.actors = actors;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ResponseEntity<ChangeRequest> createChangeRequest(final String idempotencyKey,
                                                             final ChangeRequestCreate body) {
        final CurrentActor actor = actors.current();
        final IdempotencyService.IdempotentRequest request = new IdempotencyService.IdempotentRequest(
            actor.subject(), idempotencyKey, ChangeRequestService.CREATE_OPERATION,
            IdempotencyService.fingerprint(jsonMapper.writeValueAsBytes(body)));
        final NewChangeRequestDTO draft = new NewChangeRequestDTO(body.getKind().getValue(), "console",
            body.getTargetObjectId(), body.getThoroughfareId(), body.getSummary(),
            body.getProposedLocation() == null ? null : GeoJson.fromApi(body.getProposedLocation()),
            body.getProposedHouseNumber(), null);
        final ChangeRequestDTO created = changeRequests.submitIdempotent(request, draft, actor);
        return ResponseEntity.created(URI.create("/v1/change-requests/" + created.id())).body(toApi(created));
    }

    @Override
    public ResponseEntity<ChangePage> listChanges(final OffsetDateTime since, final @Nullable String cursor,
                                                  final Integer limit) {
        throw ProblemException.notImplemented("listChanges");
    }

    static ChangeRequest toApi(final ChangeRequestDTO dto) {
        final ChangeRequest api = new ChangeRequest(dto.id(), ChangeKind.fromValue(dto.kind()),
            ChangeRequestState.fromValue(dto.state()), dto.summary(), dto.adminUnitId(), dto.createdAt());
        api.setTargetObjectId(dto.targetObjectId());
        api.setThoroughfareId(dto.thoroughfareId());
        api.setSource(ChangeRequest.SourceEnum.fromValue(dto.source()));
        return api;
    }
}
