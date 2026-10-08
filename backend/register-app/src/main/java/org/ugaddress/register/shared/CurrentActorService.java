package org.ugaddress.register.shared;

import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@link CurrentActor} of the current request from the validated access token.
 */
@Service
public class CurrentActorService {

    /** Claim carrying the custodian code (an Authentik user attribute). */
    public static final String CUSTODIAN_CLAIM = "custodian";

    /**
     * Creates the service.
     */
    public CurrentActorService() {
    }

    /**
     * Returns the actor of the current request.
     *
     * @return the authenticated actor, or {@link CurrentActor#anonymous()}
     */
    public CurrentActor current() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof final JwtAuthenticationToken token)) {
            return CurrentActor.anonymous();
        }
        final Set<String> groups = new HashSet<>();
        boolean partner = false;
        for (final GrantedAuthority authority : token.getAuthorities()) {
            final String name = authority.getAuthority();
            if (name == null) {
                continue;
            }
            if (name.startsWith(Role.AUTHORITY_PREFIX)) {
                groups.add(name.substring(Role.AUTHORITY_PREFIX.length()));
            } else if (name.equals("SCOPE_" + CurrentActor.PARTNER_SCOPE)) {
                partner = true;
            }
        }
        return new CurrentActor(token.getToken().getSubject(), token.getToken().getClaimAsString(CUSTODIAN_CLAIM),
            groups, partner);
    }
}
