package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.policy.model.Policy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory {@link PolicyManager} for the MVP, keyed by policy id. */
@Repository
public class InMemoryPolicyManager implements PolicyManager {

    private final Map<String, Policy> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Policy policy) {
        byId.put(policy.id(), policy);
    }

    @Override
    public List<Policy> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Policy> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByName(String name) {
        return byId.values().stream().anyMatch(p -> p.name().equals(name));
    }

    @Override
    public boolean delete(String id) {
        return byId.remove(id) != null;
    }
}
