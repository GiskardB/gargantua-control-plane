package ai.gargantua.controlplane.policy.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Ask the Policy Manager for a decision on a target — e.g.
 * {@code capability:send-email} — against the currently stored policies.
 *
 * @param target the selector to evaluate
 */
public record PolicyEvaluationRequest(@NotBlank String target) {
}
