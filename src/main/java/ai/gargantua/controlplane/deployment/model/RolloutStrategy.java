package ai.gargantua.controlplane.deployment.model;

/** How a new bundle version replaces the running one. */
public enum RolloutStrategy {
    RECREATE,
    ROLLING,
    CANARY
}
