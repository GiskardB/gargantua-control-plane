package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.deployment.model.Deployment;
import java.util.List;
import java.util.Optional;

/**
 * Store of deployments. A port (ADR-004): in-memory for the MVP. Holds desired
 * state only; the Runtime reconciles reality towards it.
 */
public interface DeploymentManager {

    void save(Deployment deployment);

    List<Deployment> findAll();

    Optional<Deployment> find(String id);
}
