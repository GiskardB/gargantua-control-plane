package ai.gargantua.controlplane.deployment.model;

import jakarta.validation.constraints.NotNull;

/**
 * Request to record a deployment's observed state — typically posted by the Runtime
 * as a rollout progresses.
 *
 * @param state the new lifecycle state
 */
public record DeploymentStateRequest(@NotNull DeploymentState state) {
}
