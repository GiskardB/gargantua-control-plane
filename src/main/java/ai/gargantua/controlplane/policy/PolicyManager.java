package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.policy.model.Policy;
import java.util.List;
import java.util.Optional;

/**
 * Store of governance policies. A port (ADR-004): in-memory for the MVP. Evaluation
 * is intentionally out of scope here — this subsystem defines policy; the Runtime
 * and Gateway enforce it.
 */
public interface PolicyManager {

    void save(Policy policy);

    List<Policy> findAll();

    Optional<Policy> find(String id);

    boolean existsByName(String name);

    boolean delete(String id);
}
