package org.ugaddress.register.resolve;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.nationalid.NationalId;
import org.ugaddress.register.register.AddressableObjectDTO;
import org.ugaddress.register.register.RegisterService;
import org.ugaddress.register.shared.ProblemException;

/**
 * Resolves references to addressable objects.
 *
 * <p>A reference is a national ID in any display form, or an alias written as {@code <system>:<value>}.
 */
@Service
@Transactional(readOnly = true)
public class ResolveService {

    private final RegisterService register;

    /**
     * Creates the service.
     *
     * @param register the register
     */
    public ResolveService(final RegisterService register) {
        this.register = Objects.requireNonNull(register, "register");
    }

    /**
     * Resolves a reference.
     *
     * @param reference national ID or {@code system:value} alias
     * @param partner whether the caller may see residential entrance coordinates
     * @return the resolution, or empty if nothing matches
     * @throws ProblemException with status 400 if the reference is malformed
     */
    public Optional<ResolutionDTO> resolve(final String reference, final boolean partner) {
        final String trimmed = reference.strip();
        final int colon = trimmed.indexOf(':');
        final Optional<UUID> objectId;
        final ResolutionDTO.MatchedBy matchedBy;
        if (colon > 0) {
            final String system = trimmed.substring(0, colon);
            final String value = trimmed.substring(colon + 1);
            if (value.isBlank()) {
                throw ProblemException.badRequest("An alias reference has the form <system>:<value>.");
            }
            objectId = register.findIdByAlias(system, value);
            matchedBy = ResolutionDTO.MatchedBy.ALIAS;
        } else {
            objectId = register.findIdByNationalId(NationalId.parse(trimmed));
            matchedBy = ResolutionDTO.MatchedBy.NATIONAL_ID;
        }
        return objectId.flatMap(id -> register.find(id, partner)).map(o -> new ResolutionDTO(matchedBy, o));
    }

    /**
     * Result of resolving a reference.
     *
     * @param matchedBy how the reference matched
     * @param object the matched object
     */
    public record ResolutionDTO(MatchedBy matchedBy, AddressableObjectDTO object) {

        /** How a reference matched. */
        public enum MatchedBy {
            /** By national ID. */
            NATIONAL_ID,
            /** By alias. */
            ALIAS
        }
    }
}
