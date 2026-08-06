package ai.gargantua.controlplane.registry.model;

import jakarta.validation.constraints.NotNull;

/**
 * Request to promote an already-published bundle version to an environment.
 *
 * @param environment the target environment
 */
public record PromotionRequest(@NotNull Environment environment) {
}
