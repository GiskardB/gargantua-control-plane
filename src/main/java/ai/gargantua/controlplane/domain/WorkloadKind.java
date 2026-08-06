package ai.gargantua.controlplane.domain;

/**
 * Mirrors {@code ai.gargantua.core.workload.WorkloadKind} in agent-core. Kept as
 * a local copy rather than a dependency because agent-core targets Java 21 while
 * this service runs on Java 17; the shared vocabulary is the domain model doc,
 * not a shared jar.
 */
public enum WorkloadKind {
    AGENT,
    WORKFLOW,
    EVALUATOR,
    CLASSIFIER,
    SERVICE,
    BATCH_JOB
}
