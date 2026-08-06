package ai.gargantua.controlplane.policy.model;

import java.util.List;

/**
 * The outcome of evaluating a target against the stored policies.
 *
 * <p>Semantics (MVP): a policy applies when its target matches the requested one
 * (exact, or prefix when the policy target ends in {@code *}). <b>Deny overrides</b>
 * — if any matching policy denies, the decision is DENY; otherwise ALLOW. The
 * absence of any matching policy is ALLOW (unrestricted). Enforcement happens at the
 * Runtime/Gateway; this is the Control Plane stating the decision (ADR-004).
 *
 * @param target   the evaluated selector
 * @param decision ALLOW or DENY
 * @param matched  the policies that applied, in evaluation order
 */
public record PolicyDecision(
        String target,
        PolicyEffect decision,
        List<Policy> matched) {

    public PolicyDecision {
        matched = matched == null ? List.of() : List.copyOf(matched);
    }
}
