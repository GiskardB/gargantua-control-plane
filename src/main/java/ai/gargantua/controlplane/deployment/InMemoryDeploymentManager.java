package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.deployment.model.Deployment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory {@link DeploymentManager} for the MVP, keyed by deployment id. */
@Repository
public class InMemoryDeploymentManager implements DeploymentManager {

    private final Map<String, Deployment> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Deployment deployment) {
        byId.put(deployment.id(), deployment);
    }

    @Override
    public List<Deployment> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Deployment> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
