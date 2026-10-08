package org.ugaddress.register.gazetteer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.register.gazetteer.internal.GazetteerRepository;

/**
 * Read access to admin units, thoroughfares, custodians and jurisdictions.
 */
@Service
@Transactional(readOnly = true)
public class GazetteerService {

    private final GazetteerRepository repository;

    /**
     * Creates the service.
     *
     * @param repository gazetteer storage
     */
    public GazetteerService(final GazetteerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Returns an admin unit and its ancestors.
     *
     * @param adminUnitId the smallest unit
     * @return the unit followed by its ancestors, smallest first
     */
    public List<AdminUnitDTO> adminUnitChain(final UUID adminUnitId) {
        return repository.adminUnitChain(adminUnitId);
    }

    /**
     * Finds the smallest current admin unit that contains a point.
     *
     * @param point a point in EPSG:4326
     * @return the unit id, or empty if the point is outside every unit
     */
    public Optional<UUID> smallestAdminUnitContaining(final Point point) {
        return repository.smallestAdminUnitContaining(point.getX(), point.getY());
    }

    /**
     * Finds a thoroughfare.
     *
     * @param id thoroughfare id
     * @return the thoroughfare, if it exists
     */
    public Optional<ThoroughfareDTO> thoroughfare(final UUID id) {
        return repository.thoroughfare(id);
    }

    /**
     * Finds a custodian by its code.
     *
     * @param code custodian code
     * @return the custodian, if it exists
     */
    public Optional<CustodianDTO> custodianByCode(final String code) {
        return repository.custodianByCode(code);
    }

    /**
     * Returns every admin unit a custodian may write to: its jurisdiction units and all their descendants.
     *
     * @param custodianId custodian id
     * @return admin unit ids
     */
    public Set<UUID> jurisdictionUnits(final UUID custodianId) {
        return repository.jurisdictionUnits(custodianId);
    }
}
