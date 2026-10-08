package org.ugaddress.register.register;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.nationalid.NationalId;
import org.ugaddress.register.gazetteer.GazetteerService;
import org.ugaddress.register.register.internal.RegisterRepository;
import org.ugaddress.register.shared.Cursors;

/**
 * Read access to addressable objects, their addresses, entrances, aliases and history.
 *
 * <p>Applies the public redaction rule (ADR 0009): coordinates of residential entrances are removed unless the
 * caller is a partner.
 */
@Service
@Transactional(readOnly = true)
public class RegisterService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RegisterRepository repository;
    private final GazetteerService gazetteer;

    /**
     * Creates the service.
     *
     * @param repository register storage
     * @param gazetteer gazetteer for admin unit names
     */
    public RegisterService(final RegisterRepository repository, final GazetteerService gazetteer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gazetteer = Objects.requireNonNull(gazetteer, "gazetteer");
    }

    /**
     * Finds the current object with a national ID.
     *
     * @param nationalId the ID
     * @return the object id
     */
    public Optional<UUID> findIdByNationalId(final NationalId nationalId) {
        return repository.findIdByNationalId(nationalId.digits());
    }

    /**
     * Finds the object an alias points to.
     *
     * @param system issuing system
     * @param value identifier value
     * @return the object id
     */
    public Optional<UUID> findIdByAlias(final String system, final String value) {
        return repository.findIdByAlias(system, value);
    }

    /**
     * Returns the admin unit an object belongs to.
     *
     * @param objectId object id
     * @return the admin unit id, if the object exists
     */
    public Optional<UUID> adminUnitOf(final UUID objectId) {
        return repository.adminUnitOf(objectId);
    }

    /**
     * Loads an object for a caller.
     *
     * @param objectId object id
     * @param partner whether the caller may see residential entrance coordinates
     * @return the object, redacted for non-partners
     */
    public Optional<AddressableObjectDTO> find(final UUID objectId, final boolean partner) {
        return repository.find(objectId, partner, gazetteer::adminUnitChain);
    }

    /**
     * Loads one page of an object's history.
     *
     * @param objectId object id
     * @param cursor cursor from a previous page, or {@code null}
     * @param limit page size, 1 to 100
     * @return the page, or empty if the object never existed
     */
    public Optional<HistoryPageDTO> history(final UUID objectId, final @Nullable String cursor, final int limit) {
        final OptionalLong after = Cursors.decode(cursor);
        final int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        final List<RegisterRepository.HistoryRow> rows = repository.history(objectId, after, size + 1);
        if (rows.isEmpty() && after.isEmpty()) {
            return Optional.empty();
        }
        final boolean more = rows.size() > size;
        final List<RegisterRepository.HistoryRow> page = more ? rows.subList(0, size) : rows;
        final String next = more ? Cursors.encode(page.getLast().seq()) : null;
        return Optional.of(new HistoryPageDTO(page.stream().map(RegisterRepository.HistoryRow::entry).toList(), next));
    }
}
