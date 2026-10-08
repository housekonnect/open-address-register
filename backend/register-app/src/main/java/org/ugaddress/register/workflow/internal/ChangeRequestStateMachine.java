package org.ugaddress.register.workflow.internal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.ugaddress.db.generated.enums.ChangeState;

/**
 * Allowed state transitions of a change request.
 *
 * <pre>
 * submitted ─► in_review ─► approved ─► applied
 *     │            │    └──► rejected
 *     │            └────────► withdrawn
 *     ├──► approved / rejected / withdrawn
 * </pre>
 */
public final class ChangeRequestStateMachine {

    private static final Map<ChangeState, Set<ChangeState>> TRANSITIONS = Map.of(
        ChangeState.submitted, EnumSet.of(ChangeState.in_review, ChangeState.approved, ChangeState.rejected,
            ChangeState.withdrawn),
        ChangeState.in_review, EnumSet.of(ChangeState.approved, ChangeState.rejected, ChangeState.withdrawn),
        ChangeState.approved, EnumSet.of(ChangeState.applied),
        ChangeState.rejected, EnumSet.noneOf(ChangeState.class),
        ChangeState.applied, EnumSet.noneOf(ChangeState.class),
        ChangeState.withdrawn, EnumSet.noneOf(ChangeState.class));

    private ChangeRequestStateMachine() {
    }

    /**
     * Checks whether a transition is allowed.
     *
     * @param from current state
     * @param to requested state
     * @return {@code true} if allowed
     */
    public static boolean canTransition(final ChangeState from, final ChangeState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
