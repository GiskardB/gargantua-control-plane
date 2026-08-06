package ai.gargantua.controlplane.deployment.model;

import ai.gargantua.controlplane.registry.model.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to deploy a published bundle version to an environment.
 *
 * @param bundleName    the bundle to deploy
 * @param bundleVersion the version to deploy
 * @param environment   the target environment
 * @param strategy      rollout strategy; defaults to ROLLING when omitted
 */
public record DeploymentRequest(
        @NotBlank String bundleName,
        @NotBlank String bundleVersion,
        @NotNull Environment environment,
        RolloutStrategy strategy) {

    public RolloutStrategy strategyOrDefault() {
        return strategy == null ? RolloutStrategy.ROLLING : strategy;
    }
}
