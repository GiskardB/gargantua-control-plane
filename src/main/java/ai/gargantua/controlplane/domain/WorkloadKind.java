package ai.gargantua.controlplane.domain;

/**
 * Mirrors {@code ai.gargantua.core.workload.WorkloadKind} in agent-core. Kept as a
 * local copy rather than a dependency to keep the Control Plane's build decoupled
 * from the Runtime's release cadence: the shared vocabulary is the domain model
 * doc, not a shared jar. (Both now run on Java 25, so nothing prevents adopting
 * agent-core directly later if the coupling proves worthwhile.)
 */
public enum WorkloadKind {
    AGENT,
    WORKFLOW,
    EVALUATOR,
    CLASSIFIER,
    SERVICE,
    BATCH_JOB
}
