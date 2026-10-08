package org.ugaddress.register.workflow;

import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.ugaddress.register.register.RegisterService;
import org.ugaddress.register.shared.CurrentActor;
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
        if (!actor.hasRole(Role.CUSTODIAN_APPROVER)) {
            throw ProblemException.forbidden("Only approvers may decide change requests.");
        }
        final ChangeRequestDTO current = repository.find(id)
            .orElseThrow(() -> ProblemException.notFound("No change request with this id."));
        if (current.proposedBy().equals(actor.subject())) {
            throw ProblemException.forbidden("Four-eyes rule: the proposer cannot decide their own change request.");
        }
        final ChangeState target = approve ? ChangeState.approved : ChangeState.rejected;
        if (!ChangeRequestStateMachine.canTransition(ChangeState.lookupLiteral(current.state()), target)) {
            throw ProblemException.conflict("The change request is already decided.");
        }
        final CustodianDTO custodian = custodianOf(actor);
        final Set<UUID> jurisdiction = gazetteer.jurisdictionUnits(custodian.id());
        if (!jurisdiction.contains(current.adminUnitId())) {
            throw ProblemException.forbidden("The change is outside the caller's jurisdiction.");
        }
        jurisdictionContext.enter(actor.subject(), jurisdiction);

        final ChangeRequestDTO decided = repository.decide(id, target, actor.subject());
        audit.record(new AuditEntryDTO(actor.subject(), custodian.id(), "change_request." + target.getLiteral(),
            "change_request", id, Map.of("state", target.getLiteral())));
        return decided;
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
