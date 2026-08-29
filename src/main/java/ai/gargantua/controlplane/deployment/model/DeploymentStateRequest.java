package ai.gargantua.controlplane.deployment.model;

import jakarta.validation.constraints.NotNull;

/**
 * Request to record a deployment's observed state — typically posted by the Runtime
 * as a rollout progresses.
 *
 * @param state the new lifecycle state
 * @param port  host port the runtime is reachable on, if just discovered (optional)
 */
public record DeploymentStateRequest(@NotNull DeploymentState state, Integer port) {
}
