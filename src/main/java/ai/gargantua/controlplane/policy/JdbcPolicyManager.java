package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.policy.model.Policy;
import ai.gargantua.controlplane.storage.DocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Durable {@link PolicyManager} over the {@link DocumentStore}, keyed by policy id. */
@Repository
public class JdbcPolicyManager implements PolicyManager {

    private static final String COLLECTION = "policies";

    private final DocumentStore store;

    public JdbcPolicyManager(DocumentStore store) {
        this.store = store;
    }

    @Override
    public void save(Policy policy) {
        store.put(COLLECTION, policy.id(), policy);
    }

    @Override
    public List<Policy> findAll() {
        return store.list(COLLECTION, Policy.class);
    }

    @Override
    public Optional<Policy> find(String id) {
        return store.get(COLLECTION, id, Policy.class);
    }

    @Override
    public boolean existsByName(String name) {
        return findAll().stream().anyMatch(p -> p.name().equals(name));
    }

    @Override
    public boolean delete(String id) {
        return store.delete(COLLECTION, id);
    }
}
