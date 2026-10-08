package org.ugaddress.register.shared;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Who is calling the API.
 *
 * <p>Only the opaque OIDC subject is kept; names and emails stay in the identity provider (ADR 0009).
 *
 * @param subject opaque subject identifier from the access token, or {@value #ANONYMOUS_SUBJECT}
 * @param custodianCode code of the custodian the caller works for, from the {@code custodian} claim
 * @param groups Authentik groups of the caller
 * @param partner whether the caller holds the {@code register:partner} scope
 */
public record CurrentActor(String subject, @Nullable String custodianCode, Set<String> groups, boolean partner) {

    /** Subject used for unauthenticated callers. */
    public static final String ANONYMOUS_SUBJECT = "anonymous";

    /** Scope that unlocks residential entrance coordinates. */
    public static final String PARTNER_SCOPE = "register:partner";

    /**
     * Creates an actor.
     *
     * @param subject opaque subject identifier
     * @param custodianCode custodian code, if any
     * @param groups groups of the caller
     * @param partner whether the caller is a partner
     */
    public CurrentActor {
        Objects.requireNonNull(subject, "subject");
        groups = Set.copyOf(groups);
    }

    /**
     * Returns the actor for unauthenticated requests.
     *
     * @return an anonymous, non-partner actor without groups
     */
    public static CurrentActor anonymous() {
        return new CurrentActor(ANONYMOUS_SUBJECT, null, Set.of(), false);
    }

    /**
     * Returns whether the caller presented a valid access token.
     *
     * @return {@code true} if authenticated
     */
    public boolean authenticated() {
        return !ANONYMOUS_SUBJECT.equals(subject);
    }

    /**
     * Returns whether the caller has a role.
     *
     * @param role the role
     * @return {@code true} if the caller is in the role's group
     */
    public boolean hasRole(final Role role) {
        return groups.contains(role.groupName());
    }
}
