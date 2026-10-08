package org.ugaddress.register.field;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.register.audit.AuditEntryDTO;
import org.ugaddress.register.audit.AuditService;
import org.ugaddress.register.field.internal.PhotoRepository;
import org.ugaddress.register.shared.CurrentActor;
import org.ugaddress.register.shared.IdempotencyService;
import org.ugaddress.register.shared.ProblemException;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.workflow.ChangeRequestService;
import org.ugaddress.register.workflow.NewChangeRequestDTO;

/**
 * Receives field captures: stores the photo in object storage and turns the capture into a change request.
 *
 * <p>Retries with the same {@code Idempotency-Key} return the original result: the photo key is derived from the
 * caller and the key, and the change request is created at most once.
 */
@Service
public class FieldCaptureService {

    /** Operation id of {@code POST /v1/field/captures} in the contract. */
    public static final String CREATE_OPERATION = "createFieldCapture";

    private static final Logger LOG = LoggerFactory.getLogger(FieldCaptureService.class);

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_SUMMARY = 500;

    private final ChangeRequestService changeRequests;
    private final PhotoRepository photos;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param changeRequests workflow
     * @param photos photo storage
     * @param idempotency idempotency keys
     * @param audit audit log
     */
    public FieldCaptureService(final ChangeRequestService changeRequests, final PhotoRepository photos,
                               final IdempotencyService idempotency, final AuditService audit) {
        this.changeRequests = Objects.requireNonNull(changeRequests, "changeRequests");
        this.photos = Objects.requireNonNull(photos, "photos");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /**
     * Receives a capture exactly once per idempotency key.
     *
     * @param idempotencyKey the client's key, generated when the capture was made
     * @param metadataFingerprint canonical bytes of the capture metadata
     * @param capture the capture
     * @param actor the field verifier
     * @return the result
     */
    @Transactional
    public FieldCaptureDTO capture(final String idempotencyKey, final byte[] metadataFingerprint,
                                   final NewFieldCaptureDTO capture, final CurrentActor actor) {
        if (!PHOTO_TYPES.contains(capture.photoContentType())) {
            throw ProblemException.badRequest("The photo must be a JPEG or PNG image.");
        }
        final IdempotencyService.IdempotentRequest request = new IdempotencyService.IdempotentRequest(
            actor.subject(), idempotencyKey, CREATE_OPERATION,
            IdempotencyService.fingerprint(metadataFingerprint, capture.photo()));
        final Optional<UUID> existing = idempotency.claim(request);
        if (existing.isPresent()) {
            final ChangeRequestDTO earlier = changeRequests.find(existing.get()).orElseThrow();
            return toDto(earlier);
        }

        final String photoKey = photoKey(actor.subject(), idempotencyKey, capture.photoContentType());
        photos.store(photoKey, capture.photo(), capture.photoContentType());
        // Integrity: hash what the object store actually holds, not what was received, and compare it with the hash
        // the device computed when the photo was taken. On a mismatch nothing is kept: the object is deleted and the
        // transaction (idempotency claim included) rolls back.
        final String storedSha256 = photos.sha256(photoKey);
        if (!storedSha256.equals(capture.photoSha256())) {
            photos.delete(photoKey);
            LOG.warn("Photo integrity check failed for a capture; nothing was kept");
            throw ProblemException.photoIntegrity();
        }

        final String summary = summary(capture);
        final ChangeRequestDTO created = changeRequests.submit(new NewChangeRequestDTO(
            capture.targetObjectId() == null ? "new_object" : "correction", "field", capture.targetObjectId(), null,
            summary, capture.location(), null, photoKey, storedSha256, capture.locationMocked()), actor);
        audit.record(new AuditEntryDTO(actor.subject(), created.custodianId(), "field.photo_stored", "change_request",
            created.id(), Map.of("contentType", capture.photoContentType(), "bytes", capture.photo().length,
                "sha256", storedSha256, "locationMocked", capture.locationMocked())));
        if (capture.locationMocked()) {
            LOG.info("Change request {} flagged: the device reported a mocked location", created.id());
        }
        idempotency.complete(request, created.id(), HttpURLConnection.HTTP_CREATED);
        return toDto(created);
    }

    /**
     * Loads the evidence photo of a change request for a caller who may see that request.
     *
     * @param changeRequestId change request id
     * @param actor the caller; must be a custodian approver in the request's jurisdiction
     * @return the photo
     * @throws ProblemException 403 if the caller may not see the request, 404 if there is no photo
     */
    @Transactional(readOnly = true)
    public PhotoDTO evidencePhoto(final UUID changeRequestId, final CurrentActor actor) {
        final String key = changeRequests.evidencePhotoKey(changeRequestId, actor);
        return photos.load(key)
            .map(p -> new PhotoDTO(p.content(), p.contentType()))
            .orElseThrow(() -> ProblemException.notFound("The photo of this change request is missing."));
    }

    private static FieldCaptureDTO toDto(final ChangeRequestDTO changeRequest) {
        return new FieldCaptureDTO(changeRequest.id(), changeRequest.id(), changeRequest.photoObjectKey() != null,
            changeRequest.createdAt());
    }

    private static String summary(final NewFieldCaptureDTO capture) {
        final String base = "Field capture: " + capture.kind();
        final String text = capture.note() == null || capture.note().isBlank() ? base : base + ". " + capture.note();
        return text.length() > MAX_SUMMARY ? text.substring(0, MAX_SUMMARY) : text;
    }

    private static String photoKey(final String subject, final String idempotencyKey, final String contentType) {
        final byte[] digest = IdempotencyService.fingerprint(subject.getBytes(StandardCharsets.UTF_8),
            idempotencyKey.getBytes(StandardCharsets.UTF_8));
        final String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        return "captures/" + HexFormat.of().formatHex(digest) + extension;
    }
}
