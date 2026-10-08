package org.ugaddress.register.workflow;

import java.net.HttpURLConnection;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.db.generated.enums.ChangeState;
import org.ugaddress.register.audit.AuditEntryDTO;
import org.ugaddress.register.audit.AuditService;
import org.ugaddress.register.gazetteer.CustodianDTO;
import org.ugaddress.register.gazetteer.GazetteerService;
import org.ugaddress.register.register.AddressableObjectDTO;
import org.ugaddress.register.register.ObjectApiMapper;
import org.ugaddress.register.register.RegisterService;
import org.ugaddress.register.shared.CurrentActor;
import org.ugaddress.register.shared.Cursors;
import org.ugaddress.register.shared.IdempotencyService;
import org.ugaddress.register.shared.JurisdictionContextService;
import org.ugaddress.register.shared.ProblemException;
import org.ugaddress.register.shared.Role;
import org.ugaddress.register.workflow.internal.ChangeRequestRepository;
import org.ugaddress.register.workflow.internal.ChangeRequestStateMachine;

/**
 * Submits and decides change requests.
 *
 * <p>Every write sets the jurisdiction context (row-level security) and records an audit event in the same
 * transaction. The four-eyes rule is enforced here and by a database constraint: the proposer can never decide their
 * own request.
 */
@Service
public class ChangeRequestService {

    /** Operation id of {@code POST /v1/change-requests} in the contract. */
    public static final String CREATE_OPERATION = "createChangeRequest";

    /** Operation id of {@code POST /v1/change-requests/{id}/approve} in the contract. */
    public static final String APPROVE_OPERATION = "approveChangeRequest";

    /** Operation id of {@code POST /v1/change-requests/{id}/return} in the contract. */
    public static final String RETURN_OPERATION = "returnChangeRequest";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_REASON = 3;
    private static final int MAX_REASON = 500;

    private static final Logger LOG = LoggerFactory.getLogger(ChangeRequestService.class);

    private final ChangeRequestRepository repository;
    private final GazetteerService gazetteer;
    private final RegisterService register;
    private final JurisdictionContextService jurisdictionContext;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Creates the service.
     *
     * @param repository change request storage
     * @param gazetteer gazetteer
     * @param register register
     * @param jurisdictionContext row-level security context
     * @param idempotency idempotency keys
     * @param audit audit log
     * @param events event publisher
     */
    public ChangeRequestService(final ChangeRequestRepository repository, final GazetteerService gazetteer,
                                final RegisterService register, final JurisdictionContextService jurisdictionContext,
                                final IdempotencyService idempotency, final AuditService audit,
                                final ApplicationEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gazetteer = Objects.requireNonNull(gazetteer, "gazetteer");
        this.register = Objects.requireNonNull(register, "register");
        this.jurisdictionContext = Objects.requireNonNull(jurisdictionContext, "jurisdictionContext");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Submits a change request exactly once per idempotency key.
     *
     * @param request the idempotent request
     * @param draft the change
     * @param actor the proposer
     * @return the stored (or previously stored) change request
     */
    @Transactional
    public ChangeRequestDTO submitIdempotent(final IdempotencyService.IdempotentRequest request,
                                             final NewChangeRequestDTO draft, final CurrentActor actor) {
        final Optional<UUID> existing = idempotency.claim(request);
        if (existing.isPresent()) {
            return repository.find(existing.get()).orElseThrow();
        }
        final ChangeRequestDTO created = submit(draft, actor);
        idempotency.complete(request, created.id(), HttpURLConnection.HTTP_CREATED);
        return created;
    }

    /**
     * Submits a change request. Joins the caller's transaction if there is one.
     *
     * @param draft the change
     * @param actor the proposer
     * @return the stored change request
     * @throws ProblemException 403 if the actor has no custodian or the change is outside its jurisdiction, 404 if the
     *     target does not exist, 400 if the change cannot be located
     */
    @Transactional
    public ChangeRequestDTO submit(final NewChangeRequestDTO draft, final CurrentActor actor) {
        final CustodianDTO custodian = custodianOf(actor);
        final UUID adminUnitId = locate(draft);
        final Set<UUID> jurisdiction = gazetteer.jurisdictionUnits(custodian.id());
        if (!jurisdiction.contains(adminUnitId)) {
            throw ProblemException.forbidden("The change is outside the caller's jurisdiction.");
        }
        jurisdictionContext.enter(actor.subject(), jurisdiction);

        final ChangeRequestDTO created = repository.insert(draft, adminUnitId, custodian.id(), actor.subject());
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", created.kind());
        payload.put("source", created.source());
        payload.put("adminUnitId", adminUnitId.toString());
        audit.record(new AuditEntryDTO(actor.subject(), custodian.id(), "change_request.submitted", "change_request",
            created.id(), payload));
        events.publishEvent(new ChangeRequestSubmitted(created.id(), adminUnitId, created.source()));
        LOG.info("Change request {} submitted ({}, {})", created.id(), created.kind(), created.source());
        return created;
    }

    /**
     * Finds a change request.
     *
     * @param id id
     * @return the change request
     */
    @Transactional(readOnly = true)
    public Optional<ChangeRequestDTO> find(final UUID id) {
        return repository.find(id);
    }

    /**
     * Lists change requests in the approver's jurisdiction, oldest first: the approver inbox.
     *
     * @param actor the caller; must be a custodian approver
     * @param state only this state, or {@code null} for all
     * @param cursor cursor from a previous page, or {@code null}
     * @param limit page size, 1 to 100
     * @return one page, each item with the caller's permissions
     * @throws ProblemException 403 if the caller is not an approver
     */
    @Transactional(readOnly = true)
    public ChangeRequestPageDTO inbox(final CurrentActor actor, final @Nullable String state,
                                      final @Nullable String cursor, final int limit) {
        requireApprover(actor);
        final Set<UUID> jurisdiction = gazetteer.jurisdictionUnits(custodianOf(actor).id());
        final int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        final List<ChangeRequestDTO> rows = repository.list(jurisdiction,
            state == null ? null : ChangeState.lookupLiteral(state), decodeInboxCursor(cursor), size + 1);
        final boolean more = rows.size() > size;
        final List<ChangeRequestDTO> page = more ? rows.subList(0, size) : rows;
        final String next = more
            ? Cursors.encodeKey(page.getLast().createdAt() + "|" + page.getLast().id())
            : null;
        return new ChangeRequestPageDTO(page.stream().map(r -> view(r, actor, jurisdiction)).toList(), next);
    }

    /**
     * Loads one change request for an approver, with its target, diff and the caller's permissions.
     *
     * @param id change request id
     * @param actor the caller; must be a custodian approver
     * @return the view
     * @throws ProblemException 403 if the caller is not an approver or the request is outside their jurisdiction,
     *     404 if unknown
     */
    @Transactional(readOnly = true)
    public ChangeRequestViewDTO view(final UUID id, final CurrentActor actor) {
        requireApprover(actor);
        final Set<UUID> jurisdiction = gazetteer.jurisdictionUnits(custodianOf(actor).id());
        final ChangeRequestDTO request = repository.find(id)
            .orElseThrow(() -> ProblemException.notFound("No change request with this id."));
        if (!jurisdiction.contains(request.adminUnitId())) {
            throw ProblemException.forbidden("The change request is outside the caller's jurisdiction.");
        }
        return view(request, actor, jurisdiction);
    }

    /**
     * Returns the object-storage key of a change request's evidence photo, for a caller who may see the request.
     *
     * @param id change request id
     * @param actor the caller; must be a custodian approver in the request's jurisdiction
     * @return the key
     * @throws ProblemException 403 as for {@link #view(UUID, CurrentActor)}, 404 if there is no photo
     */
    @Transactional(readOnly = true)
    public String evidencePhotoKey(final UUID id, final CurrentActor actor) {
        final String key = view(id, actor).request().photoObjectKey();
        if (key == null) {
            throw ProblemException.notFound("This change request has no photo.");
        }
        return key;
    }

    /**
     * Approves a change request exactly once per idempotency key.
     *
     * @param id change request id
     * @param request the idempotent request
     * @param actor the approver
     * @return the approved change request
     * @throws ProblemException 403 if the actor is not an approver, proposed the request (four-eyes rule) or acts
     *     outside their jurisdiction; 404 if unknown; 409 if already decided
     */
    @Transactional
    public ChangeRequestViewDTO approve(final UUID id, final IdempotencyService.IdempotentRequest request,
                                        final CurrentActor actor) {
        return decideIdempotent(id, ChangeState.approved, null, request, actor);
    }

    /**
     * Returns a change request to its proposer with a written reason, exactly once per idempotency key.
     *
     * @param id change request id
     * @param reason why it is returned (3 to 500 characters, no personal data)
     * @param request the idempotent request
     * @param actor the approver
     * @return the returned change request
     * @throws ProblemException 400 if the reason is too short or too long; otherwise as for
     *     {@link #approve(UUID, IdempotencyService.IdempotentRequest, CurrentActor)}
     */
    @Transactional
    public ChangeRequestViewDTO returnToProposer(final UUID id, final String reason,
                                                 final IdempotencyService.IdempotentRequest request,
                                                 final CurrentActor actor) {
        final String text = reason.strip();
        if (text.length() < MIN_REASON || text.length() > MAX_REASON) {
            throw ProblemException.badRequest("A reason of 3 to 500 characters is required to return a change.");
        }
        return decideIdempotent(id, ChangeState.returned, text, request, actor);
    }

    /**
     * Approves or rejects a change request.
     *
     * @param id change request id
     * @param approve {@code true} to approve, {@code false} to reject
     * @param actor the decider
     * @return the updated change request
     * @throws ProblemException 403 if the actor is not an approver, decides their own request (four-eyes rule) or
     *     acts outside their jurisdiction; 404 if unknown; 409 if the request is already decided
     */
    @Transactional
    public ChangeRequestDTO decide(final UUID id, final boolean approve, final CurrentActor actor) {
        return decide(id, approve ? ChangeState.approved : ChangeState.rejected, null, actor);
    }

    private ChangeRequestViewDTO decideIdempotent(final UUID id, final ChangeState target,
                                                  final @Nullable String reason,
                                                  final IdempotencyService.IdempotentRequest request,
                                                  final CurrentActor actor) {
        final Optional<UUID> existing = idempotency.claim(request);
        if (existing.isEmpty()) {
            decide(id, target, reason, actor);
            idempotency.complete(request, id, HttpURLConnection.HTTP_OK);
        }
        return view(id, actor);
    }

    private ChangeRequestDTO decide(final UUID id, final ChangeState target, final @Nullable String reason,
                                    final CurrentActor actor) {
        requireApprover(actor);
        final ChangeRequestDTO current = repository.find(id)
            .orElseThrow(() -> ProblemException.notFound("No change request with this id."));
        if (current.proposedBy().equals(actor.subject())) {
            throw ProblemException.forbidden("Four-eyes rule: the proposer cannot decide their own change request.");
        }
        if (!ChangeRequestStateMachine.canTransition(ChangeState.lookupLiteral(current.state()), target)) {
            throw ProblemException.conflict("The change request is already decided.");
        }
        final CustodianDTO custodian = custodianOf(actor);
        final Set<UUID> jurisdiction = gazetteer.jurisdictionUnits(custodian.id());
        if (!jurisdiction.contains(current.adminUnitId())) {
            throw ProblemException.forbidden("The change is outside the caller's jurisdiction.");
        }
        jurisdictionContext.enter(actor.subject(), jurisdiction);

        final ChangeRequestDTO decided = repository.decide(id, target, actor.subject(), reason);
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", target.getLiteral());
        payload.put("previousState", current.state());
        if (reason != null) {
            payload.put("reason", reason);
        }
        audit.record(new AuditEntryDTO(actor.subject(), custodian.id(), "change_request." + target.getLiteral(),
            "change_request", id, payload));
        LOG.info("Change request {} {}", id, target.getLiteral());
        return decided;
    }

    private static void requireApprover(final CurrentActor actor) {
        if (!actor.hasRole(Role.CUSTODIAN_APPROVER)) {
            throw ProblemException.forbidden("Only approvers may decide change requests.");
        }
    }

    private ChangeRequestViewDTO view(final ChangeRequestDTO request, final CurrentActor actor,
                                      final Set<UUID> jurisdiction) {
        final AddressableObjectDTO object = request.targetObjectId() == null
            ? null
            : register.find(request.targetObjectId(), true).orElse(null);
        return new ChangeRequestViewDTO(request, target(request, object), diff(request, object),
            denial(request, actor, jurisdiction));
    }

    private static ChangeRequestViewDTO.@Nullable Denial denial(final ChangeRequestDTO request,
                                                                final CurrentActor actor,
                                                                final Set<UUID> jurisdiction) {
        if (!actor.hasRole(Role.CUSTODIAN_APPROVER)) {
            return ChangeRequestViewDTO.Denial.NOT_APPROVER;
        }
        if (!jurisdiction.contains(request.adminUnitId())) {
            return ChangeRequestViewDTO.Denial.OUTSIDE_JURISDICTION;
        }
        if (request.proposedBy().equals(actor.subject())) {
            return ChangeRequestViewDTO.Denial.OWN_REQUEST;
        }
        final ChangeState state = ChangeState.lookupLiteral(request.state());
        if (!ChangeRequestStateMachine.canTransition(state, ChangeState.approved)) {
            return ChangeRequestViewDTO.Denial.ALREADY_DECIDED;
        }
        return null;
    }

    private ChangeRequestViewDTO.Target target(final ChangeRequestDTO request,
                                               final @Nullable AddressableObjectDTO object) {
        if (object != null) {
            final String displayId = ObjectApiMapper.displayId(object.nationalId(), object.demonstration());
            final String label = object.address() != null
                ? object.address().houseNumber() + " " + object.address().thoroughfareName()
                : object.name() != null ? object.name() : displayId;
            return new ChangeRequestViewDTO.Target(ChangeRequestViewDTO.TargetType.OBJECT, label, object.nationalId(),
                displayId, object.location());
        }
        if (request.thoroughfareId() != null) {
            final String name = gazetteer.thoroughfare(request.thoroughfareId())
                .map(t -> t.name()).orElse(request.thoroughfareId().toString());
            return new ChangeRequestViewDTO.Target(ChangeRequestViewDTO.TargetType.STREET, name, null, null,
                request.proposedLocation());
        }
        final Point location = request.proposedLocation();
        return new ChangeRequestViewDTO.Target(ChangeRequestViewDTO.TargetType.LOCATION,
            location == null ? request.summary() : format(location), null, null, location);
    }

    private static List<ChangeRequestViewDTO.Diff> diff(final ChangeRequestDTO request,
                                                        final @Nullable AddressableObjectDTO object) {
        final List<ChangeRequestViewDTO.Diff> diff = new ArrayList<>();
        if (request.proposedHouseNumber() != null) {
            diff.add(new ChangeRequestViewDTO.Diff("houseNumber",
                object == null || object.address() == null ? null : object.address().houseNumber(),
                request.proposedHouseNumber()));
        }
        if (request.proposedLocation() != null) {
            diff.add(new ChangeRequestViewDTO.Diff("location",
                object == null || object.location() == null ? null : format(object.location()),
                format(request.proposedLocation())));
        }
        return diff;
    }

    /** Latitude, longitude with six decimals (about 10 cm), the order people read coordinates in. */
    private static String format(final Point point) {
        return String.format(Locale.ROOT, "%.6f, %.6f", point.getY(), point.getX());
    }

    private static ChangeRequestRepository.@Nullable InboxPosition decodeInboxCursor(final @Nullable String cursor) {
        return Cursors.decodeKey(cursor).map(key -> {
            final int separator = key.indexOf('|');
            try {
                return new ChangeRequestRepository.InboxPosition(OffsetDateTime.parse(key.substring(0, separator)),
                    UUID.fromString(key.substring(separator + 1)));
            } catch (final DateTimeParseException | IllegalArgumentException | IndexOutOfBoundsException e) {
                throw ProblemException.badRequest("Invalid cursor.");
            }
        }).orElse(null);
    }

    private CustodianDTO custodianOf(final CurrentActor actor) {
        if (actor.custodianCode() == null) {
            throw ProblemException.forbidden("The account is not assigned to a custodian.");
        }
        return gazetteer.custodianByCode(actor.custodianCode())
            .orElseThrow(() -> ProblemException.forbidden("The account's custodian is unknown."));
    }

    private UUID locate(final NewChangeRequestDTO draft) {
        if (draft.targetObjectId() != null) {
            return register.adminUnitOf(draft.targetObjectId())
                .orElseThrow(() -> ProblemException.notFound("No addressable object with this id."));
        }
        if (draft.thoroughfareId() != null) {
            return gazetteer.thoroughfare(draft.thoroughfareId())
                .orElseThrow(() -> ProblemException.notFound("No thoroughfare with this id."))
                .adminUnitId();
        }
        if (draft.proposedLocation() != null) {
            return gazetteer.smallestAdminUnitContaining(draft.proposedLocation())
                .orElseThrow(() -> ProblemException.badRequest("The proposed location is outside every admin unit."));
        }
        throw ProblemException.badRequest("A change needs a target object, a thoroughfare or a proposed location.");
    }
}
