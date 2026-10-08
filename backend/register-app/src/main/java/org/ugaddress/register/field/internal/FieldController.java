package org.ugaddress.register.field.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.ugaddress.api.v1.FieldApi;
import org.ugaddress.api.v1.model.FieldAssignmentPage;
import org.ugaddress.api.v1.model.FieldCapture;
import org.ugaddress.api.v1.model.FieldCaptureMetadata;
import org.ugaddress.register.field.FieldCaptureDTO;
import org.ugaddress.register.field.FieldCaptureService;
import org.ugaddress.register.field.NewFieldCaptureDTO;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.GeoJson;
import org.ugaddress.register.shared.ProblemException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /v1/field/captures} and field assignments (not implemented yet).
 */
@RestController
class FieldController implements FieldApi {

    private final FieldCaptureService captures;
    private final CurrentActorService actors;
    private final JsonMapper jsonMapper;

    FieldController(final FieldCaptureService captures, final CurrentActorService actors,
                    final JsonMapper jsonMapper) {
        this.captures = captures;
        this.actors = actors;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ResponseEntity<FieldCapture> createFieldCapture(final String idempotencyKey,
                                                           final FieldCaptureMetadata metadata,
                                                           final MultipartFile photo) {
        final byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length == 0) {
            throw ProblemException.badRequest("The photo is empty.");
        }
        final String contentType = photo.getContentType() == null ? "" : photo.getContentType();
        final NewFieldCaptureDTO capture = new NewFieldCaptureDTO(metadata.getKind().getValue(),
            GeoJson.fromApi(metadata.getLocation()), metadata.getTargetObjectId(), metadata.getNote(), bytes,
            contentType);
        final FieldCaptureDTO result = captures.capture(idempotencyKey, jsonMapper.writeValueAsBytes(metadata),
            capture, actors.current());
        return ResponseEntity.created(URI.create("/v1/change-requests/" + result.changeRequestId()))
            .body(new FieldCapture(result.id(), result.changeRequestId(), result.photoStored(), result.receivedAt()));
    }

    @Override
    public ResponseEntity<FieldAssignmentPage> listFieldAssignments(final @Nullable String cursor,
                                                                    final Integer limit) {
        throw ProblemException.notImplemented("listFieldAssignments");
    }
}
