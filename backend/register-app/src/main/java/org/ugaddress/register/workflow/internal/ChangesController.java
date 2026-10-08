package org.ugaddress.register.workflow.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.ugaddress.api.v1.ChangesApi;
import org.ugaddress.api.v1.model.ChangeDiff;
import org.ugaddress.api.v1.model.ChangeEvidence;
import org.ugaddress.api.v1.model.ChangeKind;
import org.ugaddress.api.v1.model.ChangePage;
import org.ugaddress.api.v1.model.ChangePermissions;
import org.ugaddress.api.v1.model.ChangeProposal;
import org.ugaddress.api.v1.model.ChangeRequest;
import org.ugaddress.api.v1.model.ChangeRequestCreate;
import org.ugaddress.api.v1.model.ChangeRequestPage;
import org.ugaddress.api.v1.model.ChangeRequestReturn;
import org.ugaddress.api.v1.model.ChangeRequestState;
import org.ugaddress.api.v1.model.ChangeTarget;
import org.ugaddress.register.shared.CurrentActor;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.GeoJson;
import org.ugaddress.register.shared.IdempotencyService;
import org.ugaddress.register.shared.ProblemException;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.workflow.ChangeRequestPageDTO;
import org.ugaddress.register.workflow.ChangeRequestService;
import org.ugaddress.register.workflow.ChangeRequestViewDTO;
import org.ugaddress.register.workflow.NewChangeRequestDTO;
import tools.jackson.databind.json.JsonMapper;

/**
 * Change requests: submission, the approver inbox and decisions (four-eyes rule). The change feed is not
 * implemented yet.
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
            body.getProposedHouseNumber(), null, null, false);
        final ChangeRequestDTO created = changeRequests.submitIdempotent(request, draft, actor);
        return ResponseEntity.created(URI.create("/v1/change-requests/" + created.id())).body(toApi(created));
    }

    @Override
    public ResponseEntity<ChangeRequestPage> listChangeRequests(final @Nullable ChangeRequestState state,
                                                                final @Nullable String cursor, final Integer limit) {
        final ChangeRequestPageDTO page = changeRequests.inbox(actors.current(),
            state == null ? null : state.getValue(), cursor, limit);
        final ChangeRequestPage api = new ChangeRequestPage(
            page.items().stream().map(ChangesController::toApi).toList());
        api.setNextCursor(page.nextCursor());
        return ResponseEntity.ok(api);
    }

    @Override
    public ResponseEntity<ChangeRequest> getChangeRequest(final UUID id, final @Nullable String ifNoneMatch) {
        return ResponseEntity.ok(toApi(changeRequests.view(id, actors.current())));
    }

    @Override
    public ResponseEntity<ChangeRequest> approveChangeRequest(final UUID id, final String idempotencyKey) {
        final CurrentActor actor = actors.current();
        final IdempotencyService.IdempotentRequest request = new IdempotencyService.IdempotentRequest(
            actor.subject(), idempotencyKey, ChangeRequestService.APPROVE_OPERATION,
            IdempotencyService.fingerprint(id.toString().getBytes(StandardCharsets.UTF_8)));
        return ResponseEntity.ok(toApi(changeRequests.approve(id, request, actor)));
    }

    @Override
    public ResponseEntity<ChangeRequest> returnChangeRequest(final UUID id, final String idempotencyKey,
                                                             final ChangeRequestReturn body) {
        final CurrentActor actor = actors.current();
        final IdempotencyService.IdempotentRequest request = new IdempotencyService.IdempotentRequest(
            actor.subject(), idempotencyKey, ChangeRequestService.RETURN_OPERATION,
            IdempotencyService.fingerprint(id.toString().getBytes(StandardCharsets.UTF_8),
                jsonMapper.writeValueAsBytes(body)));
        return ResponseEntity.ok(toApi(changeRequests.returnToProposer(id, body.getReason(), request, actor)));
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
        api.setDecidedAt(dto.decidedAt());
        api.setDecisionReason(dto.decisionReason());
        final ChangeProposal proposal = new ChangeProposal();
        proposal.setHouseNumber(dto.proposedHouseNumber());
        proposal.setLocation(GeoJson.toApi(dto.proposedLocation()));
        api.setProposal(proposal);
        return api;
    }

    static ChangeRequest toApi(final ChangeRequestViewDTO view) {
        final ChangeRequestDTO dto = view.request();
        final ChangeRequest api = toApi(dto);
        final ChangeTarget target = new ChangeTarget(
            ChangeTarget.TypeEnum.fromValue(view.target().type().name().toLowerCase(Locale.ROOT)),
            view.target().label());
        target.setNationalId(view.target().nationalId());
        target.setDisplayId(view.target().displayId());
        target.setLocation(GeoJson.toApi(view.target().location()));
        api.setTarget(target);
        api.setDiff(view.diff().stream().map(d -> {
            final ChangeDiff diff = new ChangeDiff(ChangeDiff.FieldEnum.fromValue(d.field()));
            diff.setCurrent(d.current());
            diff.setProposed(d.proposed());
            return diff;
        }).toList());
        final ChangeEvidence evidence = new ChangeEvidence(dto.photoObjectKey() != null);
        evidence.setPhotoSha256(dto.photoSha256());
        evidence.setLocationMocked(dto.locationMocked());
        if ("field".equals(dto.source())) {
            evidence.setLocation(GeoJson.toApi(dto.proposedLocation()));
        }
        api.setEvidence(evidence);
        final ChangePermissions permissions = new ChangePermissions(view.denial() == null);
        if (view.denial() != null) {
            permissions.setReason(ChangePermissions.ReasonEnum.fromValue(
                view.denial().name().toLowerCase(Locale.ROOT)));
        }
        api.setPermissions(permissions);
        return api;
    }
}
