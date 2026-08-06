package ai.gargantua.controlplane.policy.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create a policy.
 *
 * @param name        unique policy name
 * @param description what the policy is for
 * @param target      selector the policy applies to
 * @param effect      allow or deny
 */
public record PolicyRequest(
        @NotBlank String name,
        String description,
        @NotBlank String target,
        @NotNull PolicyEffect effect) {
}
