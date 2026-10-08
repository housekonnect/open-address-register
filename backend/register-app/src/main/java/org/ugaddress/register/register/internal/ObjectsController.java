package org.ugaddress.register.register.internal;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.ugaddress.api.v1.ObjectsApi;
import org.ugaddress.api.v1.model.AddressableObject;
import org.ugaddress.api.v1.model.HistoryEntry;
import org.ugaddress.api.v1.model.HistoryPage;
import org.ugaddress.api.v1.model.Lifecycle;
import org.ugaddress.register.register.HistoryPageDTO;
import org.ugaddress.register.register.ObjectApiMapper;
import org.ugaddress.register.register.RegisterService;
import org.ugaddress.register.shared.CurrentActorService;
import org.ugaddress.register.shared.ProblemException;

/**
 * {@code GET /v1/objects/{id}} and its history. ETags are added by the shared ETag filter.
 */
@RestController
class ObjectsController implements ObjectsApi {

    private final RegisterService register;
    private final CurrentActorService actors;

    ObjectsController(final RegisterService register, final CurrentActorService actors) {
        this.register = register;
        this.actors = actors;
    }

    @Override
    public ResponseEntity<AddressableObject> getObject(final UUID id, final @Nullable String ifNoneMatch) {
        return register.find(id, actors.current().partner())
            .map(ObjectApiMapper::toApi)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> ProblemException.notFound("No addressable object with this id."));
    }

    @Override
    public ResponseEntity<HistoryPage> getObjectHistory(final UUID id, final @Nullable String cursor,
                                                        final Integer limit) {
        final HistoryPageDTO page = register.history(id, cursor, limit)
            .orElseThrow(() -> ProblemException.notFound("No addressable object with this id."));
        final HistoryPage api = new HistoryPage(page.items().stream().map(ObjectsController::entry).toList());
        api.setNextCursor(page.nextCursor());
        return ResponseEntity.ok(api);
    }

    private static HistoryEntry entry(final HistoryPageDTO.Entry entry) {
        final HistoryEntry api = new HistoryEntry(entry.recordedAt(),
            HistoryEntry.OperationEnum.fromValue(entry.operation()), entry.version());
        api.setLifecycle(Lifecycle.fromValue(entry.lifecycle()));
        api.setValidFrom(entry.validFrom());
        api.setValidTo(entry.validTo());
        return api;
    }
}
