package ai.gargantua.controlplane.deployment.model;

import ai.gargantua.controlplane.registry.model.Environment;
import java.time.Instant;

/**
 * A binding of a published bundle version to an environment — the desired state the
 * Runtime reconciles towards. Mirrors the Deployment object in the domain model.
 *
 * @param id            server-assigned identifier
 * @param bundleName    the deployed bundle's name
 * @param bundleVersion the deployed bundle's version
 * @param environment   where it is deployed
 * @param strategy      how the rollout proceeds
 * @param state         last-known lifecycle state
 * @param port          host port the runtime is reachable on, once known (null until then —
 *                      each concurrently-launched agent gets its own, see Studio's LaunchService)
 * @param createdAt     when the deployment was requested
 * @param updatedAt     when the state last changed
 */
public record Deployment(
        String id,
        String bundleName,
        String bundleVersion,
        Environment environment,
        RolloutStrategy strategy,
        DeploymentState state,
        Integer port,
        Instant createdAt,
        Instant updatedAt) {

    /** Return a copy in a new state (and port, if known — null keeps whatever was recorded before). */
    public Deployment withState(DeploymentState newState, Integer newPort, Instant at) {
        return new Deployment(
                id, bundleName, bundleVersion, environment, strategy, newState,
                newPort != null ? newPort : port, createdAt, at);
    }
}
