package ai.gargantua.controlplane.deployment.model;

/**
 * Lifecycle of a deployment as the Control Plane observes it. The Control Plane
 * records desired and last-known state; the Runtime drives the actual transitions
 * (ADR-004).
 */
public enum DeploymentState {
    PENDING,
    PROGRESSING,
    HEALTHY,
    FAILED
}
