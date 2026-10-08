package org.ugaddress.register.shared;

/**
 * Roles of register staff. Each role is an Authentik group delivered in the access token's {@code groups} claim.
 */
public enum Role {

    /** Proposes changes for their custodian's jurisdiction. */
    CUSTODIAN_EDITOR("custodian-editor"),

    /** Approves or rejects change requests proposed by someone else. */
    CUSTODIAN_APPROVER("custodian-approver"),

    /** Captures points and photos in the field. */
    FIELD_VERIFIER("field-verifier"),

    /** Administers the register on behalf of the steward ministry. */
    STEWARD_ADMIN("steward-admin");

    /** Prefix of Spring Security authorities derived from groups. */
    public static final String AUTHORITY_PREFIX = "GROUP_";

    private final String groupName;

    Role(final String groupName) {
        this.groupName = groupName;
    }

    /**
     * Returns the Authentik group name.
     *
     * @return the group name, e.g. {@code custodian-editor}
     */
    public String groupName() {
        return groupName;
    }

    /**
     * Returns the Spring Security authority granted for this role.
     *
     * @return the authority, e.g. {@code GROUP_custodian-editor}
     */
    public String authority() {
        return AUTHORITY_PREFIX + groupName;
    }
}
