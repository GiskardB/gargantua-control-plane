package ai.gargantua.controlplane.policy.model;

import java.time.Instant;

/**
 * A governance rule the Control Plane holds and the Runtime/Gateway enforce — a
 * guardrail, an RBAC grant, a rate limit. Mirrors the Policy object in the domain
 * model. The MVP stores policies as declarative records; evaluation lives at the
 * enforcement points, not here (ADR-004: the Control Plane defines, others apply).
 *
 * @param id          server-assigned identifier
 * @param name        unique, human-chosen name
 * @param description what the policy is for
 * @param target      selector the policy applies to, e.g. {@code capability:send-email}
 * @param effect      allow or deny
 * @param createdAt   when the policy was created
 */
public record Policy(
        String id,
        String name,
        String description,
        String target,
        PolicyEffect effect,
        Instant createdAt) {
}
